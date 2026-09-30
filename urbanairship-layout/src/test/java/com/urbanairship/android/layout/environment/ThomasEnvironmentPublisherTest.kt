/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.Choreographer
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
    }

    @Test
    public fun testResizesWithinAFramePublishTheFirstAtOnceAndTheLatestAtTheNextFrame() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        assertEquals(sized(410.0), publisher.environment.value)

        publisher.publish(sized(420.0))
        publisher.publish(sized(430.0))
        assertEquals(sized(410.0), publisher.environment.value)

        frames.advance()
        assertEquals(sized(430.0), publisher.environment.value)
    }

    @Test
    public fun testAContinuousResizePublishesOncePerFrame() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        publisher.publish(sized(420.0))
        frames.advance()
        assertEquals(sized(420.0), publisher.environment.value)

        publisher.publish(sized(430.0))
        assertEquals(sized(420.0), publisher.environment.value)

        frames.advance()
        assertEquals(sized(430.0), publisher.environment.value)
    }

    @Test
    public fun testAResizeAfterAQuietFramePublishesAtOnce() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))
        frames.advance()
        assertTrue(frames.isEmpty)

        publisher.publish(sized(420.0))
        assertEquals(sized(420.0), publisher.environment.value)
    }

    @Test
    public fun testAnythingButTheSizeIsPublishedAtOnceEvenMidBurst() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))
        publisher.publish(sized(420.0))

        val dark = sized(420.0).copy(darkMode = true)
        publisher.publish(dark)
        assertEquals(dark, publisher.environment.value)
        assertTrue(frames.isEmpty)
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

        frames.advance()
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
        assertTrue(frames.isEmpty)
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

    private class FakeNextFrame : ThomasEnvironmentPublisher.NextFrame {
        private val callbacks = mutableListOf<Choreographer.FrameCallback>()

        val isEmpty: Boolean get() = callbacks.isEmpty()

        override fun post(callback: Choreographer.FrameCallback) {
            callbacks.add(callback)
        }

        override fun remove(callback: Choreographer.FrameCallback) {
            callbacks.remove(callback)
        }

        /** Runs the callbacks posted before this frame; ones they post wait for the next. */
        fun advance() {
            val due = callbacks.toList()
            callbacks.clear()
            due.forEach { it.doFrame(0) }
        }
    }
}
