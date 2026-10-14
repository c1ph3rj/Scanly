package `in`.c1ph3rj.scanly.data.text

import `in`.c1ph3rj.scanly.domain.model.PageTextBlock
import `in`.c1ph3rj.scanly.domain.model.PageTextLine
import `in`.c1ph3rj.scanly.domain.model.TextBox
import org.json.JSONArray
import org.json.JSONObject

/** Compact JSON for block/line layout stored in `page_texts.blocksJson`. */
internal object PageTextLayoutCodec {
    private const val VERSION = 1

    fun encode(blocks: List<PageTextBlock>): String = JSONObject()
        .put("v", VERSION)
        .put(
            "b",
            JSONArray().apply {
                blocks.forEach { block ->
                    put(
                        JSONObject()
                            .put("t", block.text)
                            .put("r", block.box.toJson())
                            .put(
                                "l",
                                JSONArray().apply {
                                    block.lines.forEach { line ->
                                        put(JSONObject().put("t", line.text).put("r", line.box.toJson()))
                                    }
                                },
                            ),
                    )
                }
            },
        )
        .toString()

    fun decode(json: String): List<PageTextBlock> = runCatching {
        val blocks = JSONObject(json).optJSONArray("b") ?: return emptyList()
        List(blocks.length()) { index ->
            val block = blocks.getJSONObject(index)
            val lines = block.optJSONArray("l") ?: JSONArray()
            PageTextBlock(
                text = block.optString("t"),
                box = block.getJSONArray("r").toBox(),
                lines = List(lines.length()) { lineIndex ->
                    val line = lines.getJSONObject(lineIndex)
                    PageTextLine(line.optString("t"), line.getJSONArray("r").toBox())
                },
            )
        }
    }.getOrDefault(emptyList())

    private fun TextBox.toJson(): JSONArray = JSONArray()
        .put(round(left)).put(round(top)).put(round(right)).put(round(bottom))

    private fun JSONArray.toBox(): TextBox = TextBox(
        left = getDouble(0).toFloat(),
        top = getDouble(1).toFloat(),
        right = getDouble(2).toFloat(),
        bottom = getDouble(3).toFloat(),
    )

    /** Four decimals is sub-pixel on any page image and keeps rows small. */
    private fun round(value: Float): Double = Math.round(value * 10_000.0) / 10_000.0
}
