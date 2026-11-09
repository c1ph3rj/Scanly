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
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import androidx.annotation.StringRes

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
    if (isQrWebLink(value)) appString(R.string.qr_payload_link) else appString(R.string.qr_payload_text)

fun formatQrScanResultSubtitle(value: String): String =
    if (isQrWebLink(value)) appString(R.string.qr_tool_presentation_open_in_a_browser_or) else appString(R.string.qr_tool_presentation_copy_to_the_clipboard)

fun formatQrGeneratePlaceholderHint(contentBlank: Boolean): String =
    if (contentBlank) appString(R.string.qr_tool_presentation_preview_appears_as_you_type) else appString(R.string.qr_tool_presentation_generating_preview)

/**
 * Landscape hosts Scan/Create inside the scan panel. A blocked camera must still
 * show that control so Generate stays reachable without granting Camera.
 */
fun qrPermissionGateShowsModeSelector(twoPane: Boolean): Boolean = twoPane

/** Colours offered for a created code. All are dark enough to scan on white paper. */
enum class QrInkColor(@param:StringRes val labelRes: Int, val argb: Long) {
    Ink(R.string.qr_ink_color_ink, DefaultQrInkArgb),
    Teal(R.string.watermark_color_teal, 0xFF0F5F58),
    Navy(R.string.sign_ink_navy, 0xFF1E3A8A),
    Maroon(R.string.qr_ink_color_maroon, 0xFF7F1D1D),
    ;

    val label: String get() = appString(labelRes)
}

/** What the big button does for a decoded code (result screen and scan card). */
enum class QrPrimaryAction(@param:StringRes val resultLabelRes: Int, @param:StringRes val cardLabelRes: Int) {
    OpenLink(R.string.legal_document_open_in_browser, R.string.wifi_security_open),
    PayUpi(R.string.qr_primary_action_pay_with_a_upi_app, R.string.qr_primary_action_pay),
    JoinWifi(R.string.qr_primary_action_join_this_network, R.string.qr_primary_action_join),
    SaveContact(R.string.qr_primary_action_save_to_contacts, R.string.document_detail_save),
    SendEmail(R.string.qr_primary_action_send_email, R.string.qr_payload_email),
    Call(R.string.qr_primary_action_call_this_number, R.string.qr_result_call),
    CopyText(R.string.qr_primary_action_copy_text, R.string.page_text_copy),
    ;

    val resultLabel: String get() = appString(resultLabelRes)
    val cardLabel: String get() = appString(cardLabelRes)
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
fun formatQrFoundStatus(payload: QrPayload): String = appString(R.string.qr_tool_presentation_found) + when (payload) {
    is QrPayload.Link -> appString(R.string.qr_tool_presentation_a_link)
    is QrPayload.Upi -> appString(R.string.qr_tool_presentation_a_upi_payment)
    is QrPayload.Wifi -> appString(R.string.qr_tool_presentation_a_wi_fi_network)
    is QrPayload.Contact -> appString(R.string.qr_tool_presentation_a_contact)
    is QrPayload.Email -> appString(R.string.qr_tool_presentation_an_email_address)
    is QrPayload.Phone -> appString(R.string.qr_tool_presentation_a_phone_number)
    is QrPayload.Text -> appString(R.string.qr_tool_presentation_some_text)
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
        elapsed < 60_000L -> appString(R.string.qr_tool_presentation_just_now)
        minutes < 60L -> appString(R.string.qr_tool_presentation_min_ago, minutes)
        minutes < 24 * 60L -> appString(R.string.qr_tool_presentation_h_ago, minutes / 60)
        else -> "on " + Instant.ofEpochMilli(thenMillis).atZone(zone)
            .format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }
}

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

enum class QrHistoryFilter(@param:StringRes val labelRes: Int) {
    All(R.string.settings_detail_screens_all),
    Scanned(R.string.qr_history_scanned),
    Created(R.string.qr_history_created),
    ;

    val label: String get() = appString(labelRes)
}

enum class QrHistorySection(@param:StringRes val labelRes: Int) {
    Today(R.string.library_card_presentation_today),
    Yesterday(R.string.library_card_presentation_yesterday),
    Earlier(R.string.qr_history_section_earlier),
    ;

    val label: String get() = appString(labelRes)
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
