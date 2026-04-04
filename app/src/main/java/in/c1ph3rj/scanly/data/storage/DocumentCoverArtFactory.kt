package `in`.c1ph3rj.scanly.data.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import `in`.c1ph3rj.scanly.core.common.DocumentPresentationFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class DocumentCoverArtFactory @Inject constructor() {
    fun create(title: String): Bitmap {
        val bitmap = Bitmap.createBitmap(COVER_WIDTH, COVER_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val palette = palettes[title.hashCode().absoluteValue % palettes.size]

        val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                COVER_WIDTH.toFloat(),
                COVER_HEIGHT.toFloat(),
                palette.startColor,
                palette.endColor,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, COVER_WIDTH.toFloat(), COVER_HEIGHT.toFloat(), gradientPaint)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accentColor
            alpha = 90
        }
        canvas.drawCircle(COVER_WIDTH * 0.8f, COVER_HEIGHT * 0.18f, COVER_WIDTH * 0.28f, accentPaint)
        canvas.drawCircle(COVER_WIDTH * 0.18f, COVER_HEIGHT * 0.82f, COVER_WIDTH * 0.24f, accentPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            textSize = 160f
            isFakeBoldText = true
        }
        val titleBounds = Rect()
        val initials = DocumentPresentationFormatter.initials(title)
        titlePaint.getTextBounds(initials, 0, initials.length, titleBounds)
        val titleBaseline = COVER_HEIGHT / 2f - titleBounds.exactCenterY()
        canvas.drawText(initials, COVER_WIDTH / 2f, titleBaseline, titlePaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = 38f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            DocumentPresentationFormatter.normalizeTitle(title),
            COVER_WIDTH / 2f,
            COVER_HEIGHT - 72f,
            subtitlePaint,
        )

        return bitmap
    }

    private data class CoverPalette(
        val startColor: Int,
        val endColor: Int,
        val accentColor: Int,
    )

    private companion object {
        const val COVER_WIDTH = 480
        const val COVER_HEIGHT = 640

        val palettes = listOf(
            CoverPalette(Color.parseColor("#264653"), Color.parseColor("#2A9D8F"), Color.parseColor("#E9C46A")),
            CoverPalette(Color.parseColor("#1D3557"), Color.parseColor("#457B9D"), Color.parseColor("#A8DADC")),
            CoverPalette(Color.parseColor("#7F5539"), Color.parseColor("#B08968"), Color.parseColor("#EDE0D4")),
            CoverPalette(Color.parseColor("#283618"), Color.parseColor("#606C38"), Color.parseColor("#DDA15E")),
            CoverPalette(Color.parseColor("#2B2D42"), Color.parseColor("#4A4E69"), Color.parseColor("#F2E9E4")),
        )
    }
}
