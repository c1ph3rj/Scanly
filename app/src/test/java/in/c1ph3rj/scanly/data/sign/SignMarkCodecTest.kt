package `in`.c1ph3rj.scanly.data.sign

import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignMarkCodecTest {

    private val marks = listOf(
        SignMark.Image(
            id = "img-1",
            pageIndex = 0,
            box = NormRect(0.25f, 0.5f, 0.3f, 0.1f),
            ink = SignInk.BLUE,
            imagePath = "/data/signatures/sig.png",
            aspectRatio = 3f,
            kind = SignatureKind.INITIALS,
        ),
        SignMark.Text(
            id = "txt-1",
            pageIndex = 1,
            box = NormRect(0.1f, 0.2f, 0.4f, 0.05f),
            ink = SignInk.BLACK,
            text = "Jeeva Raghavan",
        ),
        SignMark.Date(
            id = "date-1",
            pageIndex = 1,
            box = NormRect(0.6f, 0.8f, 0.2f, 0.04f),
            ink = SignInk.RED,
            text = "13 Oct 2026",
        ),
        SignMark.Checkbox(
            id = "chk-1",
            pageIndex = 2,
            box = NormRect(0.05f, 0.45f, 0.03f, 0.03f),
            ink = SignInk.NAVY,
        ),
    )

    @Test
    fun roundTripsAllFourMarkTypes() {
        assertEquals(marks, SignMarkCodec.decode(SignMarkCodec.encode(marks)))
    }

    @Test
    fun encodedJsonCarriesVersionAndMarks() {
        val root = JSONObject(SignMarkCodec.encode(marks))
        assertEquals(SignMarkCodec.Version, root.getInt("v"))
        assertEquals(marks.size, root.getJSONArray("marks").length())
        assertEquals("image", root.getJSONArray("marks").getJSONObject(0).getString("type"))
    }

    @Test
    fun blankOrInvalidInputDecodesToEmpty() {
        assertTrue(SignMarkCodec.decode(null).isEmpty())
        assertTrue(SignMarkCodec.decode("").isEmpty())
        assertTrue(SignMarkCodec.decode("not json").isEmpty())
    }

    @Test
    fun unknownFutureVersionDecodesToEmpty() {
        val future = JSONObject(SignMarkCodec.encode(marks)).put("v", 99).toString()
        assertTrue(SignMarkCodec.decode(future).isEmpty())
    }

    @Test
    fun malformedEntriesAreSkippedAndValidOnesSurvive() {
        val json = """
            {"v":1,"marks":[
              {"type":"checkbox","id":"ok","page":0,"l":0.1,"t":0.1,"w":0.02,"h":0.02,"ink":"navy"},
              {"type":"stamp","id":"unknown-type","page":0,"l":0.1,"t":0.1,"w":0.02,"h":0.02,"ink":"navy"},
              {"type":"checkbox","id":" ","page":0,"l":0.1,"t":0.1,"w":0.02,"h":0.02,"ink":"navy"},
              {"type":"checkbox","id":"zero-width","page":0,"l":0.1,"t":0.1,"w":0,"h":0.02,"ink":"navy"},
              {"type":"image","id":"no-path","page":0,"l":0.1,"t":0.1,"w":0.2,"h":0.1,"path":"","aspect":2,"kind":"signature","ink":"navy"},
              {"type":"text","id":"blank-text","page":0,"l":0.1,"t":0.1,"w":0.2,"h":0.05,"text":"  ","ink":"navy"},
              42,
              {"type":"date","id":"d","page":1,"l":0.4,"t":0.6,"w":0.2,"h":0.04,"ink":"red","text":"13 Oct 2026"}
            ]}
        """.trimIndent()

        val decoded = SignMarkCodec.decode(json)

        assertEquals(listOf("ok", "d"), decoded.map { it.id })
        assertEquals(SignMark.Date("d", 1, NormRect(0.4f, 0.6f, 0.2f, 0.04f), SignInk.RED, "13 Oct 2026"), decoded[1])
    }

    @Test
    fun unknownInkFallsBackToDefault() {
        val json = """
            {"v":1,"marks":[
              {"type":"checkbox","id":"c","page":0,"l":0.1,"t":0.1,"w":0.02,"h":0.02,"ink":"magenta"}
            ]}
        """.trimIndent()

        val decoded = SignMarkCodec.decode(json)

        assertEquals(SignInk.Default, decoded.single().ink)
    }

    @Test
    fun checkboxKeepsCheckedStateAndOldDataDefaultsToTicked() {
        val unticked = SignMark.Checkbox("c", 0, NormRect(0.1f, 0.1f, 0.05f, 0.05f), SignInk.Default, checked = false)
        assertEquals(listOf(unticked), SignMarkCodec.decode(SignMarkCodec.encode(listOf(unticked))))

        val legacy = """{"v":1,"marks":[{"type":"checkbox","id":"old","page":0,"l":0.1,"t":0.1,"w":0.05,"h":0.05,"ink":"navy"}]}"""
        val decoded = SignMarkCodec.decode(legacy).single() as SignMark.Checkbox
        assertEquals(true, decoded.checked)
    }
}
