package com.urbanairship.android.layout.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tests for the sizes [ThomasImageSizeResolver] hands the image loader. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ThomasImageSizeResolverTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val displayWidth = context.resources.displayMetrics.widthPixels
    private val displayHeight = context.resources.displayMetrics.heightPixels

    private fun size(width: String, height: String) = Size(width, height)

    @Test
    fun percentResolvesToItsShareOfTheDisplay() {
        val resolver = ThomasImageSizeResolver(size("100%", "50%"), null)

        assertEquals(displayWidth, resolver.resolveWidth(context, null))
        assertEquals(displayHeight / 2, resolver.resolveHeight(context, null))
    }

    @Test
    fun percentAnswersWithoutAMeasurementOnTheOtherAxis() {
        val resolver = ThomasImageSizeResolver(size("25%", "100%"), null)

        assertEquals(displayWidth / 4, resolver.resolveWidth(context, null))
        assertEquals(displayHeight, resolver.resolveHeight(context, null))
    }

    @Test
    fun absoluteResolvesToPixels() {
        val resolver = ThomasImageSizeResolver(size("200", "100"), null)

        assertEquals(400, resolver.resolveWidth(context, null))
        assertEquals(200, resolver.resolveHeight(context, null))
    }

    /** Auto still needs the other axis and the image's own shape; without them it has no answer. */
    @Test
    fun autoWithoutAnImageSizeIsUnresolved() {
        val resolver = ThomasImageSizeResolver(size("auto", "auto"), null)

        assertNull(resolver.resolveWidth(context, 500))
        assertNull(resolver.resolveHeight(context, 500))
    }

    @Test
    fun autoScalesTheMeasuredAxisByTheImageShape() {
        val resolver = ThomasImageSizeResolver(size("auto", "auto"), android.util.Size(642, 350))

        assertEquals(350, resolver.resolveHeight(context, 642))
        assertEquals(1284, resolver.resolveWidth(context, 700))
    }

    @Test
    fun noDeclaredSizeIsUnresolved() {
        val resolver = ThomasImageSizeResolver(null, null)

        assertNull(resolver.resolveWidth(context, 500))
        assertNull(resolver.resolveHeight(context, 500))
    }
}
