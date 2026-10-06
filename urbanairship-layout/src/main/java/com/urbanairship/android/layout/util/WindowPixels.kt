/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import androidx.core.graphics.Insets

/**
 * A window's size in pixels, with and without its system bars.
 *
 * @property width the whole window's width.
 * @property height the whole window's height.
 * @property safeWidth the width left after the system bars.
 * @property safeHeight the height left after the system bars.
 */
internal data class WindowPixels(
    val width: Int,
    val height: Int,
    val safeWidth: Int,
    val safeHeight: Int
) {

    /** Returns [width], or [safeWidth] unless [ignoreSafeArea]. */
    fun width(ignoreSafeArea: Boolean): Int = if (ignoreSafeArea) width else safeWidth

    /** Returns [height], or [safeHeight] unless [ignoreSafeArea]. */
    fun height(ignoreSafeArea: Boolean): Int = if (ignoreSafeArea) height else safeHeight

    companion object {

        /**
         * Returns a window of [width] by [height] whose safe area is what [systemBars] leave.
         *
         * @param width the whole window's width.
         * @param height the whole window's height.
         * @param systemBars the system bars' insets on each edge of that window.
         * @return the window's size with and without the system bars.
         */
        fun of(width: Int, height: Int, systemBars: Insets): WindowPixels = WindowPixels(
            width = width,
            height = height,
            safeWidth = (width - systemBars.left - systemBars.right).coerceAtLeast(0),
            safeHeight = (height - systemBars.top - systemBars.bottom).coerceAtLeast(0)
        )
    }
}
