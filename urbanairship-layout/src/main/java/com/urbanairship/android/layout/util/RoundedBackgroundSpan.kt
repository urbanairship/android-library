/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.Spanned
import android.text.style.LineBackgroundSpan

/**
 * Draws a rounded background behind a `==highlighted==` run, one rectangle per line it covers.
 *
 * The geometry comes from [layout] rather than from measuring the text, so it follows whatever the
 * layout actually did with alignment and direction. One rectangle per line means a run that changes
 * direction partway — Arabic quoted inside English — gets a rectangle spanning the whole of it
 * rather than one per directional run.
 */
internal class RoundedBackgroundSpan(
    private val backgroundColor: Int,
    private val cornerRadius: Float = 0f,
    private val layout: () -> Layout?
) : LineBackgroundSpan {

    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int
    ) {
        // Every line of the paragraph is offered to every span in it, so most calls are for lines
        // this highlight has no part of.
        val spanned = text as? Spanned ?: return
        val from = spanned.getSpanStart(this).coerceAtLeast(start)
        val to = spanned.getSpanEnd(this).coerceAtMost(end)
        if (from >= to) {
            return
        }

        val laidOut = layout() ?: return

        // When the run reaches an edge of the line, take the edge from the line itself.
        // getPrimaryHorizontal would otherwise return the position of the start of the next line.
        //
        // Which edge that is depends on the paragraph: a line leads from its left in a
        // left-to-right paragraph and from its right in a right-to-left one, so in Arabic or Hebrew
        // the two are the other way round.
        val rtl = laidOut.getParagraphDirection(lineNumber) == Layout.DIR_RIGHT_TO_LEFT
        val startX = if (from <= start) {
            if (rtl) laidOut.getLineRight(lineNumber) else laidOut.getLineLeft(lineNumber)
        } else {
            laidOut.getPrimaryHorizontal(from)
        }
        val endX = if (to >= end) {
            if (rtl) laidOut.getLineLeft(lineNumber) else laidOut.getLineRight(lineNumber)
        } else {
            laidOut.getPrimaryHorizontal(to)
        }

        val rect = RectF(
            minOf(startX, endX),
            top.toFloat(),
            maxOf(startX, endX),
            bottom.toFloat()
        )

        val originalColor = paint.color
        val originalStyle = paint.style
        paint.color = backgroundColor
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.color = originalColor
        paint.style = originalStyle
    }
}
