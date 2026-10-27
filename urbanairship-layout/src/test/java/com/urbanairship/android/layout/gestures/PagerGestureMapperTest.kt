/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.gestures

import android.graphics.RectF
import com.urbanairship.android.layout.property.GestureLocation.ANY
import com.urbanairship.android.layout.property.GestureLocation.BOTTOM
import com.urbanairship.android.layout.property.GestureLocation.END
import com.urbanairship.android.layout.property.GestureLocation.LEFT
import com.urbanairship.android.layout.property.GestureLocation.RIGHT
import com.urbanairship.android.layout.property.GestureLocation.START
import com.urbanairship.android.layout.property.GestureLocation.TOP
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class PagerGestureMapperTest {

    private val bounds = RectF(0f, 0f, 300f, 300f)

    @Test
    public fun testTapLocations() {
        val mapper = PagerGestureMapper(bounds, isRtl = false)

        assertEquals(listOf(TOP, ANY), mapper.mapTap(150f, 10f))
        assertEquals(listOf(BOTTOM, ANY), mapper.mapTap(150f, 290f))
        assertEquals(listOf(LEFT, START, ANY), mapper.mapTap(10f, 150f))
        assertEquals(listOf(RIGHT, END, ANY), mapper.mapTap(290f, 150f))
        assertEquals(listOf(ANY), mapper.mapTap(150f, 150f))
    }

    @Test
    public fun testTapLocationsRtl() {
        val mapper = PagerGestureMapper(bounds, isRtl = true)

        assertEquals(listOf(LEFT, END, ANY), mapper.mapTap(10f, 150f))
        assertEquals(listOf(RIGHT, START, ANY), mapper.mapTap(290f, 150f))
    }
}
