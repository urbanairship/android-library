/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class GridConditionsPublisherTest {

    private val frames = FakeNextFrame()
    private val state = SharedState(GridState("grid", conditions = JsonMap.EMPTY_MAP))
    private val publisher = GridConditionsPublisher(state, nextFrame = frames)

    private val conditions: JsonMap get() = state.changes.value.conditions

    private fun sized(value: String) = jsonMapOf("size" to value)

    @Test
    public fun testTheFirstChangeIsPublishedAtTheNextFrame() {
        publisher.publish(sized("wide"))
        assertEquals(JsonMap.EMPTY_MAP, conditions)

        frames.advance()
        assertEquals(sized("wide"), conditions)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAChangeIsPublishedAtTheNextFrame() {
        publisher.publish(sized("wide"))
        frames.advance()

        publisher.publish(sized("narrow"))
        assertEquals(sized("wide"), conditions)

        frames.advance()
        assertEquals(sized("narrow"), conditions)
    }

    @Test
    public fun testChangesWithinAFramePublishOnlyTheLatest() {
        publisher.publish(sized("wide"))
        frames.advance()

        publisher.publish(sized("medium"))
        publisher.publish(sized("narrow"))
        assertEquals(sized("wide"), conditions)

        frames.advance()
        assertEquals(sized("narrow"), conditions)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testTheSameConditionsScheduleNothing() {
        publisher.publish(sized("wide"))
        frames.advance()

        publisher.publish(sized("wide"))
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testConditionsAlreadyInTheStateScheduleNothing() {
        val inherited = SharedState(GridState("grid", conditions = sized("wide")))
        GridConditionsPublisher(inherited, nextFrame = frames).publish(sized("wide"))
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testConditionsAnotherViewReplacedArePublishedAgain() {
        publisher.publish(sized("wide"))
        frames.advance()
        // A newer view of the same grid writes its own.
        GridConditionsPublisher(state, nextFrame = frames).publish(sized("narrow"))
        frames.advance()

        publisher.publish(sized("wide"))
        frames.advance()
        assertEquals(sized("wide"), conditions)
    }

    @Test
    public fun testLosingAConditionIsPublishedToo() {
        publisher.publish(sized("wide"))
        frames.advance()

        publisher.publish(JsonMap.EMPTY_MAP)
        frames.advance()
        assertEquals(JsonMap.EMPTY_MAP, conditions)
    }

    @Test
    public fun testCancelDropsConditionsWaitingForTheNextFrame() {
        publisher.publish(sized("wide"))

        publisher.cancel()
        assertTrue(frames.isEmpty)
        assertEquals(JsonMap.EMPTY_MAP, conditions)
    }

    @Test
    public fun testCancelledConditionsCanBePublishedAgain() {
        publisher.publish(sized("wide"))
        publisher.cancel()

        publisher.publish(sized("wide"))
        frames.advance()
        assertEquals(sized("wide"), conditions)
    }
}
