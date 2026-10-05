/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.EnvironmentConditionSelector
import com.urbanairship.android.layout.property.EnvironmentConditions
import com.urbanairship.android.layout.property.WindowSize
import com.urbanairship.android.layout.property.choose
import com.urbanairship.android.layout.property.conditionsIn
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasEnvironmentPublisherTest {

    /** A bucket per 10 of window width, and the frame's height as tall or short. */
    private val conditions: EnvironmentConditions = JsonValue.parseString(
        """
        {"environment_state": {"conditions": {
          "width": [
            {"window_width": {"at_most": 405}, "value": 400},
            {"window_width": {"at_most": 415}, "value": 410},
            {"window_width": {"at_most": 425}, "value": 420},
            {"value": 430}
          ],
          "frame": [
            {"frame_height": {"at_least": 210}, "value": "tall"},
            {"frame_height": {"at_least": 0}, "value": "short"}
          ]
        }}}
        """.trimIndent()
    ).optMap().conditionsIn("environment_state", EnvironmentConditionSelector::fromJson)

    private val frames = FakeNextFrame()
    private val publisher = ThomasEnvironmentPublisher(conditions, nextFrame = frames)

    private val initial = ThomasEnvironment(
        darkMode = false,
        textScale = 1.0,
        windowWidth = 400.0,
        windowHeight = 800.0,
        windowSize = WindowSize.MEDIUM
    )

    /** What publishing [reading] puts in `$environment`. */
    private fun published(reading: ThomasEnvironment) = PredicateEnvironment.of(reading, conditions.choose(reading))

    private fun sized(width: Double) = initial.copy(windowWidth = width)

    @Test
    public fun testTheFirstReadingIsPublishedAtOnce() {
        publisher.publish(initial)

        assertEquals(published(initial), publisher.environment.value)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAChangeIsPublishedAtTheNextFrame() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        assertEquals(published(initial), publisher.environment.value)

        frames.advance()
        assertEquals(published(sized(410.0)), publisher.environment.value)
    }

    @Test
    public fun testChangesWithinAFramePublishOnlyTheLatest() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        publisher.publish(sized(420.0))
        publisher.publish(sized(430.0))
        assertEquals(published(initial), publisher.environment.value)

        frames.advance()
        assertEquals(published(sized(430.0)), publisher.environment.value)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testAContinuousResizePublishesOncePerFrame() {
        publisher.publish(initial)

        publisher.publish(sized(410.0))
        frames.advance()
        assertEquals(published(sized(410.0)), publisher.environment.value)

        publisher.publish(sized(420.0))
        assertEquals(published(sized(410.0)), publisher.environment.value)
        frames.advance()
        assertEquals(published(sized(420.0)), publisher.environment.value)
    }

    @Test
    public fun testAResizeThatCrossesNoBreakpointPublishesNothing() {
        publisher.publish(initial)
        val before = publisher.environment.value

        publisher.publish(sized(403.0))
        assertTrue(frames.isEmpty)
        assertSame(before, publisher.environment.value)
    }

    @Test
    public fun testPublishesTheChosenValuesWithoutTheSizes() {
        publisher.publish(sized(410.0).copy(frameHeight = 300.0))

        val json = publisher.environment.value!!.toJsonValue().optMap()
        assertEquals(JsonValue.wrap(410), json.opt("conditions").optMap().opt("width"))
        assertEquals(JsonValue.wrap("tall"), json.opt("conditions").optMap().opt("frame"))
        assertFalse(json.opt("window").optMap().containsKey("width"))
        assertFalse(json.containsKey("frame"))
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
        assertEquals(published(initial), publisher.environment.value)

        frames.advance()
        assertEquals(published(dark), publisher.environment.value)
    }

    @Test
    public fun testARotationReplacesAPendingResize() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        val rotated = initial.copy(windowWidth = 800.0, windowHeight = 400.0)
        publisher.publish(rotated)
        frames.advance()

        assertEquals(published(rotated), publisher.environment.value)
    }

    @Test
    public fun testAFrameResizeWaitsForTheNextFrameAsAWindowResizeDoes() {
        publisher.publish(initial)

        val laidOut = initial.copy(frameWidth = 300.0, frameHeight = 200.0)
        publisher.publish(laidOut)
        publisher.publish(laidOut.copy(frameHeight = 220.0))
        assertEquals(published(initial), publisher.environment.value)

        frames.advance()
        assertEquals(published(laidOut.copy(frameHeight = 220.0)), publisher.environment.value)
    }

    @Test
    public fun testAnImmediateReadingPublishesAtOnceAndDropsThePendingOne() {
        publisher.publish(initial)
        publisher.publish(sized(410.0))

        val laidOut = initial.copy(frameWidth = 400.0, frameHeight = 800.0)
        publisher.publish(laidOut, immediate = true)

        assertEquals(published(laidOut), publisher.environment.value)
        assertTrue(frames.isEmpty)
    }

    @Test
    public fun testSeedingPublishesOnlyTheFirstReading() {
        val rootView = View(ApplicationProvider.getApplicationContext<Context>())

        publisher.seed(rootView)
        val seeded = requireNotNull(publisher.environment.value)
        assertNull("no frame has been laid out", seeded.conditions.opt("frame").string)

        val laidOut = initial.copy(frameWidth = 300.0, frameHeight = 300.0)
        publisher.publish(laidOut, immediate = true)
        publisher.seed(rootView)

        assertEquals(published(laidOut), publisher.environment.value)
    }

    /** A recreated root view leaves the last frame in place, and its first layout lands at once. */
    @Test
    public fun testARecreatedRootViewsFirstLayoutAppliesAtOnce() {
        val rootView = View(ApplicationProvider.getApplicationContext<Context>())
        val framed = initial.copy(frameWidth = 400.0, frameHeight = 800.0)
        publisher.publish(framed)

        publisher.seed(rootView)
        assertEquals(published(framed), publisher.environment.value)

        val rotated = framed.copy(
            windowWidth = 800.0, windowHeight = 400.0, frameWidth = 800.0, frameHeight = 400.0
        )
        publisher.publish(rotated, immediate = true)
        assertEquals(published(rotated), publisher.environment.value)
    }
}
