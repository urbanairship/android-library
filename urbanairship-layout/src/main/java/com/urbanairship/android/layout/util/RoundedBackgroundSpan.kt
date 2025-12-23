import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.style.ReplacementSpan
import kotlin.math.roundToInt

internal class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val cornerRadius: Float = 0f,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return (paint.measureText(text, start, end)).roundToInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val originalColor = paint.color
        val textWidth = paint.measureText(text, start, end)
        val bgRect = RectF(
            x,
            top.toFloat(),
            x + textWidth,
            y.toFloat() + paint.descent()
        )
        paint.color = backgroundColor
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)
        paint.color = originalColor

        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}
