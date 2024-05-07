/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.IntDef
import java.lang.ref.WeakReference
import kotlin.math.min
import kotlin.math.sin

/**
 * Utils class to generate a border radius array.
 */
internal object BorderRadius {

    /**
     * Top left border radius flag.
     */
    const val TOP_LEFT = 1

    /**
     * Top right border radius flag.
     */
    const val TOP_RIGHT = 1 shl 1

    /**
     * Bottom Right border radius flag.
     */
    const val BOTTOM_RIGHT = 1 shl 2

    /**
     * Bottom left border radius flag.
     */
    const val BOTTOM_LEFT = 1 shl 3

    /**
     * Flag for all 4 corners.
     */
    const val ALL = TOP_LEFT or TOP_RIGHT or BOTTOM_RIGHT or BOTTOM_LEFT

    /**
     * Flag for the left corners.
     */
    const val LEFT = TOP_LEFT or BOTTOM_LEFT

    /**
     * Flag for the right corners.
     */
    const val RIGHT = TOP_RIGHT or BOTTOM_RIGHT

    /**
     * Flag for the top corners.
     */
    const val TOP = TOP_LEFT or TOP_RIGHT

    /**
     * Flag for the bottom corners.
     */
    const val BOTTOM = BOTTOM_LEFT or BOTTOM_RIGHT

    /**
     * Creates the border radius array.
     *
     * @param pixels The border radius in pixels.
     * @param borderRadiusFlag THe border radius flag.
     * @return The corner radius array.
     */
    fun createRadiiArray(pixels: Float, @BorderRadiusFlag borderRadiusFlag: Int): FloatArray {
        val radii = FloatArray(8)

        // topLeftX, topLeftY, topRightX, topRightY, bottomRightX, bottomRightY, bottomLeftX, bottomLeftY
        if (borderRadiusFlag and TOP_LEFT == TOP_LEFT) {
            radii[0] = pixels
            radii[1] = pixels
        }
        if (borderRadiusFlag and TOP_RIGHT == TOP_RIGHT) {
            radii[2] = pixels
            radii[3] = pixels
        }
        if (borderRadiusFlag and BOTTOM_RIGHT == BOTTOM_RIGHT) {
            radii[4] = pixels
            radii[5] = pixels
        }
        if (borderRadiusFlag and BOTTOM_LEFT == BOTTOM_LEFT) {
            radii[6] = pixels
            radii[7] = pixels
        }
        return radii
    }

    /**
     * Applies padding to the view to avoid from overlapping the border radius.
     *
     * @param view The view.
     * @param borderRadius The border radius.
     * @param borderRadiusFlag The border radius flags.
     */
    fun applyBorderRadiusPadding(
        view: View,
        borderRadius: Float,
        @BorderRadiusFlag borderRadiusFlag: Int
    ) {
        if (view.width == 0) {
            val weakReference = WeakReference(view)
            view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    weakReference.get()?.let {
                        applyBorderRadiusPadding(it, borderRadius, borderRadiusFlag)
                        it.viewTreeObserver.removeOnPreDrawListener(this)
                    }
                    return false
                }
            })
        }
        var borderRadiusPixels = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            borderRadius,
            view.resources.displayMetrics
        )
        borderRadiusPixels = min(
            borderRadiusPixels,
            (view.height / 2).coerceAtMost(view.width / 2).toFloat()
        )
        val x = borderRadiusPixels * sin(Math.toRadians(45.0)).toFloat()
        val y = borderRadiusPixels * sin(Math.toRadians(45.0)).toFloat()
        val borderPaddingX = Math.round(borderRadiusPixels - x)
        val borderPaddingY = Math.round(borderRadiusPixels - y)
        var paddingLeft = 0
        var paddingRight = 0
        var paddingTop = 0
        var paddingBottom = 0
        if (borderRadiusFlag and TOP_LEFT == TOP_LEFT) {
            paddingLeft = borderPaddingX
            paddingTop = borderPaddingY
        }
        if (borderRadiusFlag and TOP_RIGHT == TOP_RIGHT) {
            paddingRight = borderPaddingX
            paddingTop = borderPaddingY
        }
        if (borderRadiusFlag and BOTTOM_RIGHT == BOTTOM_RIGHT) {
            paddingRight = borderPaddingX
            paddingBottom = borderPaddingY
        }
        if (borderRadiusFlag and BOTTOM_LEFT == BOTTOM_LEFT) {
            paddingLeft = borderPaddingX
            paddingBottom = borderPaddingY
        }
        view.setPadding(
            view.paddingLeft + paddingLeft,
            view.paddingTop + paddingTop,
            view.paddingRight + paddingRight,
            view.paddingBottom + paddingBottom
        )
    }

    @Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE)
    @IntDef(
        flag = true,
        value = [TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT, BOTTOM_LEFT, ALL, LEFT, RIGHT, BOTTOM, TOP]
    )
    @Retention(
        AnnotationRetention.SOURCE
    )
    annotation class BorderRadiusFlag
}
