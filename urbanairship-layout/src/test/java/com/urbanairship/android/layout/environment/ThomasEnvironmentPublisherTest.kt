/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.Choreographer
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasEnvironmentPublisherTest {

    private val frames = FakeNextFrame()
    private val publisher = ThomasEnvironmentPublisher(nextFrame = frames)

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
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAChangeIsPublishedAtTheNextFrame() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        assertEquals(initial, publisher.environment.value)

        frames.advance()
        assertEquals(sized(410.0), publisher.environment.value)
    }

    @Test
    public fun testChangesWithinAFramePublishOnlyTheLatest() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        publisher.publish(sized(420.0))
        publisher.publish(sized(430.0))
        assertEquals(initial, publisher.environment.value)

        frames.advance()
        assertEquals(sized(430.0), publisher.environment.value)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAContinuousResizePublishesOncePerFrame() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        frames.advance()
        assertEquals(sized(410.0), publisher.environment.value)

        publisher.publish(sized(420.0))
        assertEquals(sized(410.0), publisher.environment.value)
        frames.advance()
        assertEquals(sized(420.0), publisher.environment.value)
    }

    @Test
    public fun testAFrameWithNothingPendingSchedulesNoMore() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))
        frames.advance()

        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAReadingEqualToTheLatestSchedulesNothing() {
        publisher.publish(initial)
        publisher.publish(initial)

        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testANonSizeChangeAlsoWaitsForTheNextFrame() {
        publisher.publish(initial)

        val dark = initial.copy(darkMode = true)
        publisher.publish(dark)
        assertEquals(initial, publisher.environment.value)

        frames.advance()
        assertEquals(dark, publisher.environment.value)
    }

    @Test
    public fun testARotationReplacesAPendingResize() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        val rotated = initial.copy(windowWidth = 800.0, windowHeight = 400.0)
        publisher.publish(rotated)
        frames.advance()

        assertEquals(rotated, publisher.environment.value)
    }

    @Test
    public fun testAFrameResizeWaitsForTheNextFrameAsAWindowResizeDoes() {
        publisher.publish(initial)

        val laidOut = initial.copy(frameWidth = 300.0, frameHeight = 200.0)
        publisher.publish(laidOut)
        publisher.publish(laidOut.copy(frameHeight = 220.0))
        assertEquals(initial, publisher.environment.value)

        frames.advance()
        assertEquals(laidOut.copy(frameHeight = 220.0), publisher.environment.value)
    }

    @Test
    public fun testAnImmediateReadingPublishesAtOnceAndDropsThePendingOne() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        val laidOut = initial.copy(frameWidth = 400.0, frameHeight = 800.0)
        publisher.publish(laidOut, immediate = true)

        assertEquals(laidOut, publisher.environment.value)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testSeedingPublishesOnlyTheFirstReading() {
        val rootView = View(ApplicationProvider.getApplicationContext<Context>())

        publisher.seed(rootView)
        val seeded = requireNotNull(publisher.environment.value)
        assertNull(seeded.frameWidth)

        val laidOut = seeded.copy(frameWidth = 300.0, frameHeight = 200.0)
        publisher.publish(laidOut, immediate = true)
        publisher.seed(rootView)

        assertEquals(laidOut, publisher.environment.value)
    }

    /** A recreated root view leaves the last frame in place, and its first layout lands at once. */
    @Test
    public fun testARecreatedRootViewsFirstLayoutAppliesAtOnce() {
        val rootView = View(ApplicationProvider.getApplicationContext<Context>())
        val framed = initial.copy(frameWidth = 400.0, frameHeight = 800.0)
        publisher.publish(framed)

        publisher.seed(rootView)
        assertEquals(framed, publisher.environment.value)

        val rotated = framed.copy(
            windowWidth = 800.0, windowHeight = 400.0, frameWidth = 800.0, frameHeight = 400.0
        )
        publisher.publish(rotated, immediate = true)
        assertEquals(rotated, publisher.environment.value)
    }

    private fun sized(width: Double) = initial.copy(windowWidth = width)
}
