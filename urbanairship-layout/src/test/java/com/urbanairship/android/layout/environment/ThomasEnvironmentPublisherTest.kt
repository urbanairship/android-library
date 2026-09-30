/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
public class ThomasEnvironmentPublisherTest {

    private val publisher = ThomasEnvironmentPublisher(quietPeriodMillis = QUIET)

    private val initial = ThomasEnvironment(
        darkMode = false,
        textScale = 1.0,
        windowWidth = 400.0,
        windowHeight = 800.0,
        windowSize = WindowSize.MEDIUM
    )

    @Test
    public fun testTheFirstReadingIsPublishedAtOnce() {
        publisher.publish(initial)

        assertEquals(initial, publisher.environment.value)
    }

    @Test
    public fun testAResizeBurstPublishesItsFirstAndLastSizes() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        assertEquals(sized(410.0), publisher.environment.value)

        publisher.publish(sized(420.0))
        publisher.publish(sized(430.0))
        assertEquals(sized(410.0), publisher.environment.value)

        idle(QUIET)
        assertEquals(sized(430.0), publisher.environment.value)
    }

    @Test
    public fun testAResizeKeepsTheBurstOpenUntilTheSizeHoldsStill() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        publisher.publish(sized(420.0))
        idle(QUIET - 10)
        publisher.publish(sized(430.0))
        idle(QUIET - 10)
        assertEquals(sized(410.0), publisher.environment.value)

        idle(10)
        assertEquals(sized(430.0), publisher.environment.value)
    }

    @Test
    public fun testAnythingButTheSizeIsPublishedAtOnceEvenMidBurst() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))
        publisher.publish(sized(420.0))

        val dark = sized(420.0).copy(darkMode = true)
        publisher.publish(dark)
        assertEquals(dark, publisher.environment.value)

        idle(QUIET)
        assertEquals(dark, publisher.environment.value)
    }

    @Test
    public fun testAFrameResizeIsThrottledAsAWindowResizeIs() {
        publisher.publish(initial)

        val laidOut = initial.copy(frameWidth = 300.0, frameHeight = 200.0)
        publisher.publish(laidOut)
        assertEquals(laidOut, publisher.environment.value)

        publisher.publish(laidOut.copy(frameHeight = 210.0))
        publisher.publish(laidOut.copy(frameHeight = 220.0))
        assertEquals(laidOut, publisher.environment.value)

        idle(QUIET)
        assertEquals(laidOut.copy(frameHeight = 220.0), publisher.environment.value)
    }

    @Test
    public fun testARotationAppliesAtOnce() {
        publisher.publish(initial)

        val rotated = initial.copy(windowWidth = 800.0, windowHeight = 400.0)
        publisher.publish(rotated)

        assertEquals(rotated, publisher.environment.value)
    }

    @Test
    public fun testARotationAppliesAtOnceEvenMidBurst() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))
        publisher.publish(sized(420.0))

        val rotated = initial.copy(windowWidth = 800.0, windowHeight = 400.0)
        publisher.publish(rotated)

        assertEquals(rotated, publisher.environment.value)
        idle(QUIET)
        assertEquals(rotated, publisher.environment.value)
    }

    @Test
    public fun testSeedingPublishesOnlyTheFirstReading() {
        val context: Context = ApplicationProvider.getApplicationContext()

        publisher.seed(context)
        val seeded = requireNotNull(publisher.environment.value)
        assertNull(seeded.frameWidth)

        val laidOut = seeded.copy(frameWidth = 300.0, frameHeight = 200.0)
        publisher.publish(laidOut)
        publisher.seed(context)

        assertEquals(laidOut, publisher.environment.value)
    }

    /** A recreated root view leaves the last frame in place, and its first layout lands at once. */
    @Test
    public fun testARecreatedRootViewsFirstLayoutAppliesAtOnce() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val framed = initial.copy(frameWidth = 400.0, frameHeight = 800.0)
        publisher.publish(framed)

        publisher.seed(context)
        assertEquals(framed, publisher.environment.value)

        val rotated = framed.copy(
            windowWidth = 800.0, windowHeight = 400.0, frameWidth = 800.0, frameHeight = 400.0
        )
        publisher.publish(rotated)
        assertEquals(rotated, publisher.environment.value)
    }

    private fun sized(width: Double) = initial.copy(windowWidth = width)

    private fun idle(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private companion object {
        const val QUIET = 150L
    }
}
