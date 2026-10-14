package `in`.c1ph3rj.scanly.feature.tools.qr

import `in`.c1ph3rj.scanly.domain.model.QrCreateType
import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.model.QrPayload
import `in`.c1ph3rj.scanly.domain.model.QrPayloadBuilder
import `in`.c1ph3rj.scanly.domain.model.WifiSecurity
import `in`.c1ph3rj.scanly.domain.model.headline
import `in`.c1ph3rj.scanly.domain.model.summary
import `in`.c1ph3rj.scanly.domain.repository.DefaultQrInkArgb
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun isQrWebLink(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.startsWith("https://", ignoreCase = true) ||
        trimmed.startsWith("http://", ignoreCase = true)
}

/** Trimmed HTTP(S) URL to open, or null if [value] is not a web link. */
fun qrWebLinkToOpen(value: String): String? =
    value.trim().takeIf(::isQrWebLink)

fun qrUriIsOpenableWebLink(scheme: String?): Boolean {
    val normalized = scheme?.lowercase() ?: return false
    return normalized == "http" || normalized == "https"
}

fun formatQrScanResultTitle(value: String): String =
    if (isQrWebLink(value)) "Link" else "Text"

fun formatQrScanResultSubtitle(value: String): String =
    if (isQrWebLink(value)) "Open in a browser or copy" else "Copy to the clipboard"

fun formatQrGeneratePlaceholderHint(contentBlank: Boolean): String =
    if (contentBlank) "Preview appears as you type" else "Generating preview…"

/**
 * Landscape hosts Scan/Create inside the scan panel. A blocked camera must still
 * show that control so Generate stays reachable without granting Camera.
 */
fun qrPermissionGateShowsModeSelector(twoPane: Boolean): Boolean = twoPane

/** Colours offered for a created code. All are dark enough to scan on white paper. */
enum class QrInkColor(val label: String, val argb: Long) {
    Ink("Ink", DefaultQrInkArgb),
    Teal("Teal", 0xFF0F5F58),
    Navy("Navy", 0xFF1E3A8A),
    Maroon("Maroon", 0xFF7F1D1D),
}

/** What the big button does for a decoded code (result screen and scan card). */
enum class QrPrimaryAction(val resultLabel: String, val cardLabel: String) {
    OpenLink("Open in browser", "Open"),
    PayUpi("Pay with a UPI app", "Pay"),
    JoinWifi("Join this network", "Join"),
    SaveContact("Save to contacts", "Save"),
    SendEmail("Send email", "Email"),
    Call("Call this number", "Call"),
    CopyText("Copy text", "Copy"),
}

fun qrPrimaryActionFor(payload: QrPayload): QrPrimaryAction = when (payload) {
    is QrPayload.Link -> QrPrimaryAction.OpenLink
    is QrPayload.Upi -> QrPrimaryAction.PayUpi
    is QrPayload.Wifi -> QrPrimaryAction.JoinWifi
    is QrPayload.Contact -> QrPrimaryAction.SaveContact
    is QrPayload.Email -> QrPrimaryAction.SendEmail
    is QrPayload.Phone -> QrPrimaryAction.Call
    is QrPayload.Text -> QrPrimaryAction.CopyText
}

/** Status pill text while the camera is looking at a code ("Found a link"). */
fun formatQrFoundStatus(payload: QrPayload): String = "Found " + when (payload) {
    is QrPayload.Link -> "a link"
    is QrPayload.Upi -> "a UPI payment"
    is QrPayload.Wifi -> "a Wi-Fi network"
    is QrPayload.Contact -> "a contact"
    is QrPayload.Email -> "an email address"
    is QrPayload.Phone -> "a phone number"
    is QrPayload.Text -> "some text"
}

/** A link split into the parts the full-address block styles differently. */
data class QrLinkAddress(val scheme: String, val host: String, val rest: String)

/** Splits "https://host/path?x" into "https://", "host" and "/path?x". */
fun qrLinkAddressParts(url: String): QrLinkAddress {
    val schemeEnd = url.indexOf("://")
    if (schemeEnd < 0) return QrLinkAddress(scheme = "", host = url, rest = "")
    val scheme = url.substring(0, schemeEnd + 3)
    val afterScheme = url.substring(schemeEnd + 3)
    val host = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
    return QrLinkAddress(scheme = scheme, host = host, rest = afterScheme.substring(host.length))
}

/** Dots shown in place of a Wi-Fi password; the length is capped so it does not leak detail. */
fun qrWifiPasswordMask(password: String): String = "•".repeat(password.length.coerceIn(1, 16))

/** "Scanned 3 min ago", "Created just now", "Scanned 2 h ago". */
fun formatQrRelativeTime(thenMillis: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val elapsed = nowMillis - thenMillis
    val minutes = elapsed / 60_000L
    return when {
        elapsed < 60_000L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 24 * 60L -> "${minutes / 60} h ago"
        else -> "on " + Instant.ofEpochMilli(thenMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

enum class QrHistoryFilter(val label: String) {
    All("All"),
    Scanned("Scanned"),
    Created("Created"),
}

enum class QrHistorySection(val label: String) {
    Today("Today"),
    Yesterday("Yesterday"),
    Earlier("Earlier"),
}

fun qrHistoryMatches(entry: QrHistoryEntry, query: String, filter: QrHistoryFilter): Boolean {
    val matchesFilter = when (filter) {
        QrHistoryFilter.All -> true
        QrHistoryFilter.Scanned -> entry.origin == QrOrigin.Scanned
        QrHistoryFilter.Created -> entry.origin == QrOrigin.Created
    }
    if (!matchesFilter) return false
    val needle = query.trim()
    if (needle.isEmpty()) return true
    val payload = entry.payload
    return listOf(payload.headline, payload.summary, entry.raw).any { text ->
        text.contains(needle, ignoreCase = true)
    }
}

fun qrHistorySectionOf(
    createdAtMillis: Long,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): QrHistorySection {
    val day = Instant.ofEpochMilli(createdAtMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (day) {
        today -> QrHistorySection.Today
        today.minusDays(1) -> QrHistorySection.Yesterday
        else -> QrHistorySection.Earlier
    }
}

/** Row time as "7:31 AM" in the device locale. */
fun formatQrHistoryTime(createdAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    Instant.ofEpochMilli(createdAtMillis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

// ---------------------------------------------------------------------------------------------
// Create
// ---------------------------------------------------------------------------------------------

const val QR_CREATE_TEXT_MAX_LENGTH = 300
const val QR_CREATE_CAPTION_MAX_LENGTH = 32

/** Everything the Create tab edits. Empty by default, so the preview starts as a placeholder. */
data class QrCreateDraft(
    val type: QrCreateType = QrCreateType.Link,
    val url: String = "",
    val text: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiSecurity: WifiSecurity = WifiSecurity.WPA,
    val upiAddress: String = "",
    val upiPayee: String = "",
    val upiAmount: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val caption: String = "",
    val ink: QrInkColor = QrInkColor.Ink,
)

/** The text to encode for [draft], or null while the inputs are not yet a complete code. */
fun qrCreatePayload(draft: QrCreateDraft): String? {
    val payload = when (draft.type) {
        QrCreateType.Link -> qrLinkPayload(draft.url)
        QrCreateType.Text -> QrPayloadBuilder.text(draft.text)
        QrCreateType.Wifi -> {
            val needsPassword = draft.wifiSecurity != WifiSecurity.OPEN
            if (draft.wifiSsid.isBlank() || (needsPassword && draft.wifiPassword.isEmpty())) {
                ""
            } else {
                QrPayloadBuilder.wifi(draft.wifiSsid, draft.wifiPassword, draft.wifiSecurity)
            }
        }
        QrCreateType.Upi -> {
            if (draft.upiAddress.isBlank() || !qrUpiAmountIsValid(draft.upiAmount)) {
                ""
            } else {
                QrPayloadBuilder.upi(draft.upiAddress, draft.upiPayee, draft.upiAmount)
            }
        }
        QrCreateType.Contact -> QrPayloadBuilder.contact(
            draft.contactName,
            draft.contactPhone,
            draft.contactEmail,
        )
    }
    return payload.takeIf { it.isNotBlank() }
}

/** An https URL for a web address typed without a scheme; empty when it has no host. */
fun qrLinkPayload(input: String): String {
    val normalized = QrPayloadBuilder.link(input)
    val hasHost = Regex("://[^/\\s]+\\.[^/\\s]+").containsMatchIn(normalized)
    return if (hasHost && normalized.none(Char::isWhitespace)) normalized else ""
}

/** Blank is allowed (the amount is optional); anything else must be a positive number. */
fun qrUpiAmountIsValid(input: String): Boolean =
    input.isBlank() || QrPayloadBuilder.normalizeAmount(input) != null
