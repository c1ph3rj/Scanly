package `in`.c1ph3rj.scanly.domain.model

data class NormalizedTextPoint(
    val x: Float,
    val y: Float,
)

data class RecognizedTextToken(
    val index: Int,
    val text: String,
    val blockIndex: Int,
    val lineIndex: Int,
    val cornerPoints: List<NormalizedTextPoint>,
)

data class RecognizedPageText(
    val tokens: List<RecognizedTextToken>,
) {
    fun textForSelection(selection: IntRange): String {
        val selectedTokens = tokens.filter { token -> token.index in selection }
        if (selectedTokens.isEmpty()) return ""

        return buildString {
            selectedTokens.forEachIndexed { tokenIndex, token ->
                append(token.text)
                val nextToken = selectedTokens.getOrNull(tokenIndex + 1) ?: return@forEachIndexed
                append(if (token.lineIndex == nextToken.lineIndex) ' ' else '\n')
            }
        }
    }
}
