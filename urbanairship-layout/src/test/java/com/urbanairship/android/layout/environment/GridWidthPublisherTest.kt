/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class GridWidthPublisherTest {

    private val frames = FakeNextFrame()
    private val state = SharedState(GridState("grid", width = null))
    private val publisher = GridWidthPublisher(state, nextFrame = frames)

    private val width: Float? get() = state.changes.value.width

    @Test
    public fun testAnImmediateWidthIsPublishedAtOnce() {
        publisher.publish(320f, immediate = true)

        assertEquals(320f, width)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAChangeIsPublishedAtTheNextFrame() {
        publisher.publish(320f, immediate = true)

        publisher.publish(300f)
        assertEquals(320f, width)

        frames.advance()
        assertEquals(300f, width)
    }

    @Test
    public fun testChangesWithinAFramePublishOnlyTheLatest() {
        publisher.publish(320f, immediate = true)

        publisher.publish(310f)
        publisher.publish(300f)
        publisher.publish(290f)
        assertEquals(320f, width)

        frames.advance()
        assertEquals(290f, width)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testTheSameWidthSchedulesNothing() {
        publisher.publish(320f, immediate = true)

        publisher.publish(320f)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testLosingTheWidthIsPublishedToo() {
        publisher.publish(320f, immediate = true)

        publisher.publish(null)
        frames.advance()
        assertNull(width)
    }

    @Test
    public fun testAnImmediateWidthReplacesOneWaitingForTheNextFrame() {
        publisher.publish(320f, immediate = true)
        publisher.publish(300f)

        publisher.publish(280f, immediate = true)
        assertEquals(280f, width)
        assertTrue(frames.isEmpty)
    }
}
