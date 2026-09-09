/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ModalTransitionTest {

    @Test
    public fun testFade() {
        val json = """
            { "in": { "type": "fade", "duration_seconds": 0.2 },
              "out": { "type": "fade", "duration_seconds": 0.3 } }
        """.trimIndent()

        val transition = ModalTransition.fromJson(JsonValue.parseString(json))
        assertEquals(ModalTransitionEffect.Fade(0.2.seconds), transition.enter)
        assertEquals(ModalTransitionEffect.Fade(0.3.seconds), transition.exit)
    }

    @Test
    public fun testSlideWithDifferentEdgesInAndOut() {
        val json = """
            { "in": { "type": "slide", "edge": { "horizontal": "center", "vertical": "top" } },
              "out": { "type": "slide", "edge": { "horizontal": "center", "vertical": "bottom" } } }
        """.trimIndent()

        val transition = ModalTransition.fromJson(JsonValue.parseString(json))
        val enter = transition.enter as ModalTransitionEffect.Slide
        val exit = transition.exit as ModalTransitionEffect.Slide
        assertEquals(VerticalPosition.TOP, enter.edge.vertical)
        assertEquals(VerticalPosition.BOTTOM, exit.edge.vertical)
    }

    @Test
    public fun testDifferentEffectsInAndOut() {
        val json = """
            { "in": { "type": "explode", "corner": { "horizontal": "start", "vertical": "top" } },
              "out": { "type": "fade" } }
        """.trimIndent()

        val transition = ModalTransition.fromJson(JsonValue.parseString(json))
        assertEquals(ModalTransitionEffect.Type.EXPLODE, transition.enter.type)
        assertEquals(ModalTransitionEffect.Type.FADE, transition.exit.type)
    }

    @Test
    public fun testRoundTrip() {
        val transition = ModalTransition(
            enter = ModalTransitionEffect.Explode(
                corner = CornerPosition(HorizontalEdge.START, VerticalEdge.BOTTOM),
                duration = 0.6.seconds
            ),
            exit = ModalTransitionEffect.Fade(duration = 0.4.seconds)
        )

        // CornerPosition/EdgePosition aren't data classes, so they compare by
        // reference -- check the fields that actually carry the value instead.
        val roundTripped = ModalTransition.fromJson(transition.toJsonValue())
        val enter = roundTripped.enter as ModalTransitionEffect.Explode
        val exit = roundTripped.exit as ModalTransitionEffect.Fade
        assertEquals(HorizontalEdge.START, enter.corner.horizontal)
        assertEquals(VerticalEdge.BOTTOM, enter.corner.vertical)
        assertEquals(0.6.seconds, enter.duration)
        assertEquals(0.4.seconds, exit.duration)
    }
}
