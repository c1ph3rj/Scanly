package `in`.c1ph3rj.scanly.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ReleaseMarkdown(
    bodyMarkdown: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val blocks = remember(bodyMarkdown) { ReleaseMarkdownParser.parse(bodyMarkdown) }
    val blockSpacing = if (compact) 8.dp else 10.dp
    val listItemSpacing = if (compact) 6.dp else 8.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(blockSpacing),
    ) {
        if (blocks.isEmpty()) {
            Text(
                text = "No release notes were provided for this version.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(block, compact = compact)
                is MarkdownBlock.BulletList -> MarkdownBulletList(block, itemSpacing = listItemSpacing)
                is MarkdownBlock.Paragraph -> MarkdownParagraph(block.text, compact = compact)
            }
        }
    }
}

@Composable
private fun MarkdownHeading(
    block: MarkdownBlock.Heading,
    compact: Boolean,
) {
    val style = when (block.level) {
        1 -> if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
        2 -> if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    MarkdownText(
        text = block.text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun MarkdownBulletList(
    block: MarkdownBlock.BulletList,
    itemSpacing: Dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(itemSpacing)) {
        block.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                )
                MarkdownText(
                    text = item,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun MarkdownParagraph(
    text: String,
    compact: Boolean,
) {
    MarkdownText(
        text = text,
        style = if (compact) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MarkdownText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight? = null,
) {
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeColor = MaterialTheme.colorScheme.primary
    val annotatedText = remember(text, codeBackground, codeColor) {
        buildInlineMarkdownText(
            text = text,
            codeBackground = codeBackground,
            codeColor = codeColor,
        )
    }

    Text(
        text = annotatedText,
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
    )
}

internal object ReleaseMarkdownParser {
    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraphLines = mutableListOf<String>()
        val bulletItems = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphLines.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(" "))
                paragraphLines.clear()
            }
        }

        fun flushBullets() {
            if (bulletItems.isNotEmpty()) {
                blocks += MarkdownBlock.BulletList(bulletItems.toList())
                bulletItems.clear()
            }
        }

        markdown.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val headingMatch = headingRegex.matchEntire(line)

            when {
                line.isBlank() -> {
                    flushParagraph()
                    flushBullets()
                }

                headingMatch != null -> {
                    flushParagraph()
                    flushBullets()
                    blocks += MarkdownBlock.Heading(
                        level = headingMatch.groupValues[1].length,
                        text = headingMatch.groupValues[2].trim(),
                    )
                }

                line.startsWith("- ") || line.startsWith("* ") -> {
                    flushParagraph()
                    bulletItems += line.drop(2).trim()
                }

                else -> {
                    flushBullets()
                    paragraphLines += line
                }
            }
        }

        flushParagraph()
        flushBullets()
        return blocks
    }

    private val headingRegex = Regex("^(#{1,6})\\s+(.+)$")
}

internal sealed interface MarkdownBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MarkdownBlock

    data class BulletList(
        val items: List<String>,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
    ) : MarkdownBlock
}

private fun buildInlineMarkdownText(
    text: String,
    codeBackground: Color,
    codeColor: Color,
): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val nextBold = text.indexOf("**", startIndex = index).takeIf { it >= 0 }
        val nextCode = text.indexOf('`', startIndex = index).takeIf { it >= 0 }
        val nextToken = listOfNotNull(nextBold, nextCode).minOrNull()

        if (nextToken == null) {
            append(text.substring(index))
            break
        }

        if (nextToken > index) {
            append(text.substring(index, nextToken))
        }

        when (nextToken) {
            nextBold -> {
                val close = text.indexOf("**", startIndex = nextToken + 2)
                if (close >= 0) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(text.substring(nextToken + 2, close))
                    }
                    index = close + 2
                } else {
                    append(text.substring(nextToken))
                    break
                }
            }

            nextCode -> {
                val close = text.indexOf('`', startIndex = nextToken + 1)
                if (close >= 0) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            color = codeColor,
                        ),
                    ) {
                        append(text.substring(nextToken + 1, close))
                    }
                    index = close + 1
                } else {
                    append(text.substring(nextToken))
                    break
                }
            }
        }
    }
}
