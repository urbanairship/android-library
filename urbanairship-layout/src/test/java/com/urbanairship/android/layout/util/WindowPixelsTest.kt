/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.Context
import android.view.View
import androidx.core.graphics.Insets
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
public class WindowPixelsTest {

    @Test
    public fun testTheSafeAreaIsTheWindowLessItsSystemBars() {
        val window = WindowPixels.of(1080, 2400, Insets.of(10, 80, 20, 120))

        assertEquals(WindowPixels(1080, 2400, 1050, 2200), window)
        assertEquals(1080, window.width(ignoreSafeArea = true))
        assertEquals(1050, window.width(ignoreSafeArea = false))
        assertEquals(2400, window.height(ignoreSafeArea = true))
        assertEquals(2200, window.height(ignoreSafeArea = false))
    }

    @Test
    public fun testTheSafeAreaIsNeverNegative() {
        val window = WindowPixels.of(100, 100, Insets.of(80, 80, 80, 80))

        assertEquals(0, window.safeWidth)
        assertEquals(0, window.safeHeight)
    }

    @Test
    @Config(sdk = [29])
    public fun testBelowApi30AViewNotInAWindowFallsBackToTheDisplayMetrics() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val metrics = context.resources.displayMetrics

        val window = ResourceUtils.getWindowPixels(context, View(context))

        assertEquals(
            WindowPixels(metrics.widthPixels, metrics.heightPixels, metrics.widthPixels, metrics.heightPixels),
            window
        )
    }
}
