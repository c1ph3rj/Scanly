package `in`.c1ph3rj.scanly.feature.tools.qr

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import `in`.c1ph3rj.scanly.domain.model.QrPayload
import `in`.c1ph3rj.scanly.domain.model.WifiSecurity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/*
 * Device side of the QR flow: intents, clipboard and ML Kit decoding. Every action returns
 * the snackbar text to show when something needs saying, or null when nothing does.
 */

/** Launches [intent]; returns [failure] when no app can handle it, otherwise null. */
private fun Context.launchQr(intent: Intent, failure: String): String? =
    try {
        startActivity(intent)
        null
    } catch (_: ActivityNotFoundException) {
        failure
    }

/** Copies [text] to the clipboard. */
fun copyQrText(context: Context, label: String, text: String): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    return "Copied to clipboard"
}

/** Opens the system share sheet with [text]. */
fun shareQrText(context: Context, text: String): String? {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    return context.launchQr(
        intent = Intent.createChooser(send, "Share QR code"),
        failure = "Nothing can receive this text",
    )
}

/** Opens an http(s) link in the browser. Other schemes are refused. */
fun openQrWebLink(context: Context, url: String): String? {
    val target = qrWebLinkToOpen(url) ?: return "Not a web link"
    val uri = Uri.parse(target)
    if (!qrUriIsOpenableWebLink(uri.scheme)) return "Not a web link"
    return context.launchQr(Intent(Intent.ACTION_VIEW, uri), "No app can open this link")
}

/**
 * Hands the UPI link to a payment app. Scanly never makes the payment: the chooser lists the
 * apps that accept upi:// links and the payee and amount are confirmed there.
 */
fun payWithUpi(context: Context, raw: String): String? {
    val view = Intent(Intent.ACTION_VIEW, Uri.parse(raw))
    return context.launchQr(
        intent = Intent.createChooser(view, "Pay with"),
        failure = "No UPI app can open this code",
    )
}

/**
 * Joins a Wi-Fi network. On Android 11+ (WPA or open) the system adds it as a suggestion in
 * one tap. Otherwise, or for WEP, the password is copied and Wi-Fi settings open.
 */
fun joinQrWifi(context: Context, wifi: QrPayload.Wifi): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wifi.security != WifiSecurity.WEP) {
        val suggestion = buildWifiSuggestion(wifi)
        if (suggestion != null) {
            val add = Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(
                Settings.EXTRA_WIFI_NETWORK_LIST,
                arrayListOf(suggestion),
            )
            return context.launchQr(add, "Wi-Fi settings can't add this network")
        }
    }
    val password = wifi.password
    if (password != null) copyQrText(context, "Wi-Fi password", password)
    val failure = context.launchQr(
        intent = Intent(Settings.ACTION_WIFI_SETTINGS),
        failure = "Wi-Fi settings can't be opened",
    )
    return failure ?: if (password != null) {
        "Password copied — pick the network"
    } else {
        "Pick \"${wifi.ssid}\" in Wi-Fi settings"
    }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun buildWifiSuggestion(wifi: QrPayload.Wifi): WifiNetworkSuggestion? {
    if (wifi.security == WifiSecurity.WEP) return null
    return runCatching {
        val builder = WifiNetworkSuggestion.Builder()
            .setSsid(wifi.ssid)
            .setIsHiddenSsid(wifi.hidden)
        if (wifi.security == WifiSecurity.WPA) {
            builder.setWpa2Passphrase(wifi.password ?: error("Missing passphrase"))
        }
        builder.build()
    }.getOrNull()
}

/** Opens the contacts editor with the card's name, phone and email filled in. */
fun saveQrContact(context: Context, contact: QrPayload.Contact): String? {
    val insert = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        contact.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        contact.phone?.let { putExtra(ContactsContract.Intents.Insert.PHONE, it) }
        contact.email?.let { putExtra(ContactsContract.Intents.Insert.EMAIL, it) }
        contact.organization?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
    }
    return context.launchQr(insert, "No contacts app can save this")
}

/** Opens the dialer with [number]. The call is placed by the user, not by Scanly. */
fun dialQrNumber(context: Context, number: String): String? =
    context.launchQr(
        intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)),
        failure = "No phone app can open this number",
    )

fun composeQrEmail(context: Context, email: QrPayload.Email): String? {
    val compose = Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", email.address, null)).apply {
        email.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
    }
    return context.launchQr(compose, "No email app is set up on this phone")
}

fun webSearchQr(context: Context, query: String): String? {
    val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
    return context.launchQr(search, "No app can search the web")
}

/** Runs the main action for [payload]. */
fun runQrPrimaryAction(context: Context, payload: QrPayload): String? = when (payload) {
    is QrPayload.Link -> openQrWebLink(context, payload.url)
    is QrPayload.Upi -> payWithUpi(context, payload.raw)
    is QrPayload.Wifi -> joinQrWifi(context, payload)
    is QrPayload.Contact -> saveQrContact(context, payload)
    is QrPayload.Email -> composeQrEmail(context, payload)
    is QrPayload.Phone -> dialQrNumber(context, payload.number)
    is QrPayload.Text -> copyQrText(context, "QR text", payload.raw)
}

/** Scanner for QR and the other common 1D/2D formats. Null if ML Kit cannot start. */
fun createQrBarcodeScanner(): BarcodeScanner? =
    runCatching { BarcodeScanning.getClient(qrBarcodeOptions()) }.getOrNull()

private fun qrBarcodeOptions(): BarcodeScannerOptions =
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_EAN_13,
        )
        .build()

/** Decodes the first readable code in a picked photo. Null when none is found or the file fails. */
suspend fun decodeQrFromPhoto(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    val scanner = createQrBarcodeScanner() ?: return@withContext null
    try {
        val photo = decodeQrPhoto(context, uri) ?: return@withContext null
        val barcodes = scanner.process(InputImage.fromBitmap(photo.bitmap, photo.rotationDegrees))
            .awaitBarcodes()
        // Processing has finished, so the pixels are no longer needed.
        photo.bitmap.recycle()
        barcodes.firstNotNullOfOrNull { barcode -> barcode.rawValue?.takeIf(String::isNotBlank) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: OutOfMemoryError) {
        null
    } finally {
        scanner.close()
    }
}

/** A photo decoded at a size ML Kit can handle, with the rotation its EXIF data asks for. */
private class QrPhoto(val bitmap: Bitmap, val rotationDegrees: Int)

/** Longest edge the photo is decoded to; QR codes stay readable at this size. */
private const val MAX_QR_PHOTO_EDGE_PX = 2560

/**
 * Decodes [uri] downsampled so its longest edge is at most [MAX_QR_PHOTO_EDGE_PX]. Decoding a
 * full-resolution camera photo can exhaust memory before ML Kit even runs.
 */
private fun decodeQrPhoto(context: Context, uri: Uri): QrPhoto? {
    val resolver = context.contentResolver
    val rotation = runCatching {
        resolver.openInputStream(uri)?.use { stream -> ExifInterface(stream).rotationDegrees }
    }.getOrNull() ?: 0

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
    var sampleSize = 1
    while (longEdge / sampleSize > MAX_QR_PHOTO_EDGE_PX) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = resolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    } ?: return null
    return QrPhoto(bitmap, rotation)
}

private suspend fun Task<List<Barcode>>.awaitBarcodes(): List<Barcode> =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { barcodes -> continuation.resume(barcodes) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
    }
