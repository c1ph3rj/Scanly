package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadTest {

    @Test
    fun linksExposeHostAndSecurity() {
        val link = QrPayloadParser.parse("  https://www.Scanly.c1ph3rj.in/get?x=1 ") as QrPayload.Link
        assertEquals("scanly.c1ph3rj.in", link.host)
        assertTrue(link.isSecure)
        assertEquals("Link · https", link.summary)

        val bare = QrPayloadParser.parse("www.example.com") as QrPayload.Link
        assertEquals("https://www.example.com", bare.url)

        val plain = QrPayloadParser.parse("http://example.com") as QrPayload.Link
        assertFalse(plain.isSecure)
    }

    @Test
    fun upiParametersAreDecoded() {
        val upi = QrPayloadParser.parse("upi://pay?pa=genzhut@bank&pn=GenZ%20Hut&am=250.00&cu=INR") as QrPayload.Upi
        assertEquals("genzhut@bank", upi.payeeAddress)
        assertEquals("GenZ Hut", upi.payeeName)
        assertEquals("250.00", upi.amount)
        assertEquals("Pay GenZ Hut", upi.headline)
    }

    @Test
    fun upiWithoutPayeeFallsBackToText() {
        assertTrue(QrPayloadParser.parse("upi://pay?pn=Nobody") is QrPayload.Text)
    }

    @Test
    fun wifiHandlesEscapesAndSecurity() {
        val wifi = QrPayloadParser.parse("WIFI:T:WPA;S:Cafe\\;Bar;P:p\\:ss\\\\word;H:true;;") as QrPayload.Wifi
        assertEquals("Cafe;Bar", wifi.ssid)
        assertEquals("p:ss\\word", wifi.password)
        assertEquals(WifiSecurity.WPA, wifi.security)
        assertTrue(wifi.hidden)

        val open = QrPayloadParser.parse("WIFI:S:Guest;;") as QrPayload.Wifi
        assertEquals(WifiSecurity.OPEN, open.security)
        assertNull(open.password)
    }

    @Test
    fun builtWifiRoundTrips() {
        val raw = QrPayloadBuilder.wifi("Studio; 2", "a,b:c", WifiSecurity.WPA)
        val parsed = QrPayloadParser.parse(raw) as QrPayload.Wifi
        assertEquals("Studio; 2", parsed.ssid)
        assertEquals("a,b:c", parsed.password)
        assertEquals(WifiSecurity.WPA, parsed.security)
        assertEquals("", QrPayloadBuilder.wifi(" ", "x", WifiSecurity.WPA))
        assertFalse(QrPayloadBuilder.wifi("Open net", "ignored", WifiSecurity.OPEN).contains("P:"))
    }

    @Test
    fun contactsParseFromMeCardAndVCard() {
        val me = QrPayloadParser.parse("MECARD:N:Hut,GenZ;TEL:+911234;EMAIL:hi@genz.in;;") as QrPayload.Contact
        assertEquals("GenZ Hut", me.name)
        assertEquals("+911234", me.phone)
        assertEquals("hi@genz.in", me.email)

        val vcard = QrPayloadParser.parse(
            "BEGIN:VCARD\nVERSION:3.0\nN:Hut;GenZ\nTEL;TYPE=CELL:+91 98\nEMAIL:a@b.c\nEND:VCARD",
        ) as QrPayload.Contact
        assertEquals("GenZ Hut", vcard.name)
        assertEquals("+91 98", vcard.phone)
    }

    @Test
    fun builtContactAndUpiRoundTrip() {
        val contact = QrPayloadParser.parse(QrPayloadBuilder.contact("Asha; R", "+91 1", "")) as QrPayload.Contact
        assertEquals("Asha; R", contact.name)
        assertEquals("+91 1", contact.phone)
        assertNull(contact.email)

        val upiRaw = QrPayloadBuilder.upi("shop@okbank", "Corner Shop", "₹1,250.5")
        val upi = QrPayloadParser.parse(upiRaw) as QrPayload.Upi
        assertEquals("Corner Shop", upi.payeeName)
        assertEquals("1250.50", upi.amount)
        assertFalse(QrPayloadBuilder.upi("shop@okbank", "", "").contains("am="))
    }

    @Test
    fun amountsAreNormalisedOrDropped() {
        assertEquals("250.00", QrPayloadBuilder.normalizeAmount("250"))
        assertNull(QrPayloadBuilder.normalizeAmount("0"))
        assertNull(QrPayloadBuilder.normalizeAmount("abc"))
    }

    @Test
    fun linkBuilderAddsHttpsOnlyWhenMissing() {
        assertEquals("https://scanly.app", QrPayloadBuilder.link("scanly.app"))
        assertEquals("http://x.y", QrPayloadBuilder.link(" http://x.y "))
        assertEquals("", QrPayloadBuilder.link("  "))
    }

    @Test
    fun emailPhoneAndTextAreRecognised() {
        val mail = QrPayloadParser.parse("mailto:hi@scanly.app?subject=Hello%20there") as QrPayload.Email
        assertEquals("hi@scanly.app", mail.address)
        assertEquals("Hello there", mail.subject)
        assertEquals("+91 99", (QrPayloadParser.parse("tel:+91 99") as QrPayload.Phone).number)
        val text = QrPayloadParser.parse("\nhello world\nsecond") as QrPayload.Text
        assertEquals("hello world", text.headline)
    }
}
