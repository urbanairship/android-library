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
    public fun testTheFirstWidthIsPublishedAtTheNextFrame() {
        publisher.publish(320f)
        assertNull(width)

        frames.advance()
        assertEquals(320f, width)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAChangeIsPublishedAtTheNextFrame() {
        publisher.publish(320f)
        frames.advance()

        publisher.publish(300f)
        assertEquals(320f, width)

        frames.advance()
        assertEquals(300f, width)
    }

    @Test
    public fun testChangesWithinAFramePublishOnlyTheLatest() {
        publisher.publish(320f)
        frames.advance()

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
        publisher.publish(320f)
        frames.advance()

        publisher.publish(320f)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAFirstWidthOfNoneIsStillPublished() {
        val stale = SharedState(GridState("grid", width = 320f))
        val fresh = GridWidthPublisher(stale, nextFrame = frames)

        fresh.publish(null)
        frames.advance()
        assertNull(stale.changes.value.width)
    }

    @Test
    public fun testAWidthAnotherViewReplacedIsPublishedAgain() {
        publisher.publish(320f)
        frames.advance()
        // A newer view of the same grid writes its own width.
        GridWidthPublisher(state, nextFrame = frames).publish(300f)
        frames.advance()

        publisher.publish(320f)
        frames.advance()
        assertEquals(320f, width)
    }

    @Test
    public fun testAWidthAlreadyInTheStateSchedulesNothing() {
        val inherited = SharedState(GridState("grid", width = 320f))
        GridWidthPublisher(inherited, nextFrame = frames).publish(320f)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testLosingTheWidthIsPublishedToo() {
        publisher.publish(320f)
        frames.advance()

        publisher.publish(null)
        frames.advance()
        assertNull(width)
    }

    @Test
    public fun testCancelDropsAWidthWaitingForTheNextFrame() {
        publisher.publish(320f)

        publisher.cancel()
        assertTrue(frames.isEmpty)
        assertNull(width)
    }

    @Test
    public fun testACancelledWidthCanBePublishedAgain() {
        publisher.publish(320f)
        publisher.cancel()

        publisher.publish(320f)
        frames.advance()
        assertEquals(320f, width)
    }
}
