package `in`.c1ph3rj.scanly.domain.model

import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * A decoded QR code, shaped by what it contains (design: QrResult "result by type").
 * [raw] always holds the exact scanned text so the "Raw content" view stays truthful.
 */
sealed interface QrPayload {
    val raw: String

    data class Link(override val raw: String, val url: String) : QrPayload {
        /** Lower-cased host without a leading "www.", or the URL itself when unparsable. */
        val host: String
            get() = runCatching { java.net.URI(url).host }.getOrNull()
                ?.lowercase(Locale.ROOT)?.removePrefix("www.")
                ?: url
        val isSecure: Boolean get() = url.startsWith("https://", ignoreCase = true)
    }

    data class Upi(
        override val raw: String,
        val payeeAddress: String,
        val payeeName: String?,
        val amount: String?,
        val note: String?,
    ) : QrPayload

    data class Wifi(
        override val raw: String,
        val ssid: String,
        val password: String?,
        val security: WifiSecurity,
        val hidden: Boolean,
    ) : QrPayload

    data class Contact(
        override val raw: String,
        val name: String?,
        val phone: String?,
        val email: String?,
        val organization: String?,
    ) : QrPayload

    data class Email(override val raw: String, val address: String, val subject: String?) : QrPayload

    data class Phone(override val raw: String, val number: String) : QrPayload

    data class Text(override val raw: String) : QrPayload
}

enum class WifiSecurity(val label: String, val token: String) {
    WPA("WPA/WPA2", "WPA"),
    WEP("WEP", "WEP"),
    OPEN("Open", "nopass"),
}

/** The type picker on the Create tab, in design order. */
enum class QrCreateType(val label: String) {
    Link("Link"),
    Text("Text"),
    Wifi("Wi-Fi"),
    Upi("UPI"),
    Contact("Contact"),
}

/** One-word kind used for headers, history rows and filters ("LINK", "WI-FI"). */
val QrPayload.kindLabel: String
    get() = when (this) {
        is QrPayload.Link -> "Link"
        is QrPayload.Upi -> "UPI payment"
        is QrPayload.Wifi -> "Wi-Fi"
        is QrPayload.Contact -> "Contact"
        is QrPayload.Email -> "Email"
        is QrPayload.Phone -> "Phone"
        is QrPayload.Text -> "Text"
    }

/** Main line for a result card or history row. */
val QrPayload.headline: String
    get() = when (this) {
        is QrPayload.Link -> host
        is QrPayload.Upi -> payeeName?.let { "Pay $it" } ?: payeeAddress
        is QrPayload.Wifi -> ssid.ifBlank { "Wi-Fi network" }
        is QrPayload.Contact -> name ?: phone ?: email ?: "Contact"
        is QrPayload.Email -> address
        is QrPayload.Phone -> number
        is QrPayload.Text -> raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "Empty text"
    }

/** Secondary line ("Link · https", "Wi-Fi · WPA2", "Text · 68 characters"). */
val QrPayload.summary: String
    get() = when (this) {
        is QrPayload.Link -> if (isSecure) "Link · https" else "Link · not secure (http)"
        is QrPayload.Upi -> "UPI · $payeeAddress"
        is QrPayload.Wifi -> "Wi-Fi · ${security.label}"
        is QrPayload.Contact -> "Contact card"
        is QrPayload.Email -> "Email"
        is QrPayload.Phone -> "Phone number"
        is QrPayload.Text -> "Text · ${raw.length} characters"
    }

object QrPayloadParser {

    fun parse(rawInput: String): QrPayload {
        val raw = rawInput.trim()
        val lower = raw.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") ->
                QrPayload.Link(raw = raw, url = raw)
            lower.startsWith("www.") && !raw.contains(' ') ->
                QrPayload.Link(raw = raw, url = "https://$raw")
            lower.startsWith("upi://") -> parseUpi(raw) ?: QrPayload.Text(raw)
            lower.startsWith("wifi:") -> parseWifi(raw) ?: QrPayload.Text(raw)
            lower.startsWith("mecard:") -> parseMeCard(raw)
            lower.startsWith("begin:vcard") -> parseVCard(raw)
            lower.startsWith("mailto:") -> parseMailto(raw)
            lower.startsWith("matmsg:") -> parseMatMsg(raw)
            lower.startsWith("tel:") -> QrPayload.Phone(raw, raw.substring(4).trim())
            else -> QrPayload.Text(raw)
        }
    }

    private fun parseUpi(raw: String): QrPayload.Upi? {
        val query = raw.substringAfter('?', missingDelimiterValue = "")
        val params = query.split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', "").lowercase(Locale.ROOT)
                if (key.isEmpty()) null else key to decode(part.substringAfter('=', ""))
            }
            .toMap()
        val address = params["pa"]?.takeIf { it.isNotBlank() } ?: return null
        return QrPayload.Upi(
            raw = raw,
            payeeAddress = address,
            payeeName = params["pn"]?.takeIf { it.isNotBlank() },
            amount = params["am"]?.takeIf { it.isNotBlank() },
            note = params["tn"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseWifi(raw: String): QrPayload.Wifi? {
        val fields = splitEscapedFields(raw.substring(5))
        val ssid = fields["S"] ?: return null
        val type = fields["T"]?.uppercase(Locale.ROOT)
        val security = when {
            type == null || type.isEmpty() || type == "NOPASS" -> {
                if (fields["P"].isNullOrEmpty()) WifiSecurity.OPEN else WifiSecurity.WPA
            }
            type == "WEP" -> WifiSecurity.WEP
            else -> WifiSecurity.WPA
        }
        return QrPayload.Wifi(
            raw = raw,
            ssid = ssid,
            password = fields["P"]?.takeIf { it.isNotEmpty() },
            security = security,
            hidden = fields["H"].equals("true", ignoreCase = true),
        )
    }

    private fun parseMeCard(raw: String): QrPayload.Contact {
        val fields = splitEscapedFields(raw.substring(7))
        val name = fields["N"]?.let { value ->
            // MECARD names are "Last,First".
            val parts = value.split(',').map(String::trim).filter(String::isNotEmpty)
            if (parts.size == 2) "${parts[1]} ${parts[0]}" else value
        }
        return QrPayload.Contact(
            raw = raw,
            name = name?.takeIf { it.isNotBlank() },
            phone = fields["TEL"]?.takeIf { it.isNotBlank() },
            email = fields["EMAIL"]?.takeIf { it.isNotBlank() },
            organization = fields["ORG"]?.takeIf { it.isNotBlank() },
        )
    }

    private fun parseVCard(raw: String): QrPayload.Contact {
        var name: String? = null
        var structuredName: String? = null
        var phone: String? = null
        var email: String? = null
        var org: String? = null
        raw.lineSequence().forEach { line ->
            val key = line.substringBefore(':').substringBefore(';').uppercase(Locale.ROOT)
            val value = line.substringAfter(':', "").trim()
            when (key) {
                "FN" -> if (value.isNotEmpty()) name = value
                "N" -> if (value.isNotEmpty()) {
                    val parts = value.split(';')
                    structuredName = listOfNotNull(parts.getOrNull(1), parts.getOrNull(0))
                        .filter(String::isNotBlank).joinToString(" ")
                }
                "TEL" -> if (phone == null && value.isNotEmpty()) phone = value
                "EMAIL" -> if (email == null && value.isNotEmpty()) email = value
                "ORG" -> if (value.isNotEmpty()) org = value.replace(';', ' ').trim()
            }
        }
        return QrPayload.Contact(
            raw = raw,
            name = name ?: structuredName?.takeIf { it.isNotBlank() },
            phone = phone,
            email = email,
            organization = org,
        )
    }

    private fun parseMailto(raw: String): QrPayload {
        val body = raw.substring(7)
        val address = decode(body.substringBefore('?')).trim()
        if (address.isEmpty()) return QrPayload.Text(raw)
        val subject = body.substringAfter('?', "").split('&')
            .firstOrNull { it.startsWith("subject=", ignoreCase = true) }
            ?.substringAfter('=')?.let(::decode)
        return QrPayload.Email(raw, address, subject?.takeIf { it.isNotBlank() })
    }

    private fun parseMatMsg(raw: String): QrPayload {
        val fields = splitEscapedFields(raw.substring(7))
        val address = fields["TO"]?.takeIf { it.isNotBlank() } ?: return QrPayload.Text(raw)
        return QrPayload.Email(raw, address, fields["SUB"]?.takeIf { it.isNotBlank() })
    }

    /** Splits "K:v;K2:v2;;" honouring backslash escapes (\; \, \: \\ \"). */
    internal fun splitEscapedFields(body: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val token = StringBuilder()
        var escaped = false
        fun flush() {
            val text = token.toString()
            token.clear()
            if (text.isEmpty()) return
            val colon = indexOfUnescaped(text, ':')
            if (colon <= 0) return
            val key = text.substring(0, colon).uppercase(Locale.ROOT)
            if (key !in result) result[key] = unescape(text.substring(colon + 1))
        }
        for (char in body) {
            when {
                escaped -> {
                    token.append('\\').append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == ';' -> flush()
                else -> token.append(char)
            }
        }
        flush()
        return result
    }

    private fun indexOfUnescaped(text: String, target: Char): Int {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '\\') {
                index += 2
                continue
            }
            if (char == target) return index
            index += 1
        }
        return -1
    }

    private fun unescape(value: String): String {
        val out = StringBuilder()
        var escaped = false
        for (char in value) {
            if (escaped) {
                out.append(char)
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                out.append(char)
            }
        }
        return out.toString()
    }

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}

/** Builds standard QR payload strings for the Create tab. */
object QrPayloadBuilder {

    fun link(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(trimmed)
        return if (hasScheme) trimmed else "https://$trimmed"
    }

    fun text(input: String): String = input.trim()

    fun wifi(ssid: String, password: String, security: WifiSecurity, hidden: Boolean = false): String {
        if (ssid.isBlank()) return ""
        return buildString {
            append("WIFI:")
            append("T:").append(security.token).append(';')
            append("S:").append(escapeField(ssid)).append(';')
            if (security != WifiSecurity.OPEN && password.isNotEmpty()) {
                append("P:").append(escapeField(password)).append(';')
            }
            if (hidden) append("H:true;")
            append(';')
        }
    }

    /** NPCI UPI deep link. Amount is optional so the payer can type it. */
    fun upi(payeeAddress: String, payeeName: String, amount: String, note: String = ""): String {
        val address = payeeAddress.trim()
        if (address.isEmpty()) return ""
        val params = buildList {
            add("pa=${encode(address)}")
            if (payeeName.isNotBlank()) add("pn=${encode(payeeName.trim())}")
            normalizeAmount(amount)?.let { add("am=$it") }
            add("cu=INR")
            if (note.isNotBlank()) add("tn=${encode(note.trim())}")
        }
        return "upi://pay?" + params.joinToString("&")
    }

    /** MECARD keeps codes small; scanners on Android and iOS both import it. */
    fun contact(name: String, phone: String, email: String): String {
        if (name.isBlank() && phone.isBlank() && email.isBlank()) return ""
        return buildString {
            append("MECARD:")
            if (name.isNotBlank()) append("N:").append(escapeField(name.trim())).append(';')
            if (phone.isNotBlank()) append("TEL:").append(escapeField(phone.trim())).append(';')
            if (email.isNotBlank()) append("EMAIL:").append(escapeField(email.trim())).append(';')
            append(';')
        }
    }

    /** "250", "250.5" → "250.00" / "250.50"; invalid or zero → null. */
    fun normalizeAmount(input: String): String? {
        val cleaned = input.trim().removePrefix("₹").replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
    }

    private fun escapeField(value: String): String =
        value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
            .replace("\"", "\\\"")

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
