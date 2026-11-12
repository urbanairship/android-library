/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import kotlin.math.roundToInt

/** A script run's text size, as a fraction of the label's. */
internal const val SCRIPT_FONT_SCALE = 0.65f

/** How far a superscript's baseline is raised, as a fraction of the label's text size. */
internal const val SUPERSCRIPT_BASELINE_SCALE = 0.4f

/** How far a subscript's baseline is lowered, as a fraction of the label's text size. */
internal const val SUBSCRIPT_BASELINE_SCALE = 0.2f

/**
 * Draws a `^^superscript^^` or `,{subscript},` run smaller and off the baseline, sized from the
 * label's text size rather than the paint's.
 *
 * Nested scripts each add their shift and set the same size, so `^^a,{b},^^` lowers `b` from `a`'s
 * baseline without shrinking it again. The framework's spans scale from the paint, which an outer
 * span has already changed.
 *
 * @param baseTextSize The label's text size, in pixels.
 * @param superscript `true` to raise the run, `false` to lower it.
 */
internal class ScriptSpan(
    private val baseTextSize: Float,
    private val superscript: Boolean
) : MetricAffectingSpan() {

    override fun updateDrawState(tp: TextPaint) = apply(tp)

    override fun updateMeasureState(tp: TextPaint) = apply(tp)

    private fun apply(tp: TextPaint) {
        tp.textSize = baseTextSize * SCRIPT_FONT_SCALE
        tp.baselineShift += if (superscript) {
            -(baseTextSize * SUPERSCRIPT_BASELINE_SCALE).roundToInt()
        } else {
            (baseTextSize * SUBSCRIPT_BASELINE_SCALE).roundToInt()
        }
    }
}
