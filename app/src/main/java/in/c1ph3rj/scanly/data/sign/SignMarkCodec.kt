package `in`.c1ph3rj.scanly.data.sign

import `in`.c1ph3rj.scanly.domain.model.NormRect
import `in`.c1ph3rj.scanly.domain.model.SignInk
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import org.json.JSONArray
import org.json.JSONObject

/** Compact JSON for the sign marks stored with a page's markup. */
internal object SignMarkCodec {
    const val Version = 1

    /** Encodes [marks] as `{"v":1,"marks":[...]}`. */
    fun encode(marks: List<SignMark>): String = JSONObject()
        .put("v", Version)
        .put("marks", JSONArray().apply { marks.forEach { put(it.toJson()) } })
        .toString()

    /**
     * Decodes marks written by [encode]. Unknown types and malformed entries are skipped; a
     * malformed root or an unknown future version returns an empty list.
     */
    fun decode(json: String?): List<SignMark> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(json)
            if (root.optInt("v", -1) != Version) return emptyList()
            val marks = root.optJSONArray("marks") ?: return emptyList()
            List(marks.length()) { index ->
                runCatching { marks.getJSONObject(index).toMark() }.getOrNull()
            }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    private fun SignMark.toJson(): JSONObject = when (this) {
        is SignMark.Image -> baseJson("image")
            .put("path", imagePath)
            .put("aspect", aspectRatio.toDouble())
            .put("kind", kind.storageValue)

        is SignMark.Text -> baseJson("text").put("text", text)
        is SignMark.Date -> baseJson("date").put("text", text)
        is SignMark.Checkbox -> baseJson("checkbox")
    }

    private fun SignMark.baseJson(type: String): JSONObject = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("page", pageIndex)
        .put("l", box.left.toDouble())
        .put("t", box.top.toDouble())
        .put("w", box.width.toDouble())
        .put("h", box.height.toDouble())
        .put("ink", ink.storageValue)

    /** Returns null for any entry that cannot form a valid mark. */
    private fun JSONObject.toMark(): SignMark? {
        val id = optString("id")
        if (id.isBlank()) return null

        val left = optDouble("l", Double.NaN)
        val top = optDouble("t", Double.NaN)
        val width = optDouble("w", Double.NaN)
        val height = optDouble("h", Double.NaN)
        if (!listOf(left, top, width, height).all { it.isFinite() }) return null
        if (width <= 0.0 || height <= 0.0) return null

        val page = optInt("page", -1)
        if (page < 0) return null

        val box = NormRect(left.toFloat(), top.toFloat(), width.toFloat(), height.toFloat())
        val ink = SignInk.fromStorage(optString("ink"))

        return when (optString("type")) {
            "image" -> {
                val path = optString("path")
                if (path.isBlank()) return null
                val fallbackAspect = width / height
                val aspect = optDouble("aspect", fallbackAspect)
                    .takeIf { it.isFinite() && it > 0.0 } ?: fallbackAspect
                SignMark.Image(
                    id = id,
                    pageIndex = page,
                    box = box,
                    ink = ink,
                    imagePath = path,
                    aspectRatio = aspect.toFloat(),
                    kind = SignatureKind.fromStorage(optString("kind")),
                )
            }

            "text" -> {
                val text = optString("text")
                if (text.isBlank()) return null
                SignMark.Text(id, page, box, ink, text)
            }

            "date" -> {
                val text = optString("text")
                if (text.isBlank()) return null
                SignMark.Date(id, page, box, ink, text)
            }

            "checkbox" -> SignMark.Checkbox(id, page, box, ink)
            else -> null
        }
    }
}
