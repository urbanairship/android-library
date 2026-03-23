import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spanned
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
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
        // Draw background rect
        val originalColor = paint.color
        val textWidth = paint.measureText(text, start, end)
        val bgRect = RectF(x, top.toFloat(), x + textWidth, y.toFloat() + paint.descent())
        paint.color = backgroundColor
        canvas.drawRoundRect(bgRect, cornerRadius, cornerRadius, paint)
        paint.color = originalColor

        // Draw text, applying any nested MetricAffectingSpans (e.g. SubscriptSpan, SuperscriptSpan)
        // per segment. ReplacementSpan.draw() bypasses the normal span pipeline, so we must
        // manually apply baseline shifts and font size changes from nested spans.
        if (text is Spanned) {
            val subSpans = text.getSpans(start, end, MetricAffectingSpan::class.java)
            if (subSpans.isNotEmpty()) {
                val boundaries = sortedSetOf(start, end)
                for (span in subSpans) {
                    boundaries += text.getSpanStart(span).coerceIn(start, end)
                    boundaries += text.getSpanEnd(span).coerceIn(start, end)
                }
                val boundaryList = boundaries.toList()
                var curX = x
                for (i in 0 until boundaryList.size - 1) {
                    val segStart = boundaryList[i]
                    val segEnd = boundaryList[i + 1]
                    if (segStart >= segEnd) continue
                    val tp = TextPaint(paint)
                    text.getSpans(segStart, segEnd, MetricAffectingSpan::class.java)
                        .forEach { span -> span.updateDrawState(tp) }
                    canvas.drawText(text, segStart, segEnd, curX, y.toFloat() + tp.baselineShift, tp)
                    curX += tp.measureText(text, segStart, segEnd)
                }
                return
            }
        }
        canvas.drawText(text, start, end, x, y.toFloat(), paint)
    }
}
