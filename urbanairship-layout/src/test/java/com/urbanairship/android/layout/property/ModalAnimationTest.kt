/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ModalAnimationTest {

    @Test
    public fun testFade() {
        val json = """
            { "in": { "type": "fade", "duration_seconds": 0.2 },
              "out": { "type": "fade", "duration_seconds": 0.3 } }
        """.trimIndent()

        val animation = ModalAnimation.fromJson(JsonValue.parseString(json))
        assertEquals(ModalAnimationEffect.Fade(0.2.seconds), animation.enter)
        assertEquals(ModalAnimationEffect.Fade(0.3.seconds), animation.exit)
    }

    @Test
    public fun testSlideWithDifferentEdgesInAndOut() {
        val json = """
            { "in": { "type": "slide", "edge": { "horizontal": "center", "vertical": "top" } },
              "out": { "type": "slide", "edge": { "horizontal": "center", "vertical": "bottom" } } }
        """.trimIndent()

        val animation = ModalAnimation.fromJson(JsonValue.parseString(json))
        val enter = animation.enter as ModalAnimationEffect.Slide
        val exit = animation.exit as ModalAnimationEffect.Slide
        assertEquals(VerticalPosition.TOP, enter.edge.vertical)
        assertEquals(VerticalPosition.BOTTOM, exit.edge.vertical)
    }

    @Test
    public fun testDifferentEffectsInAndOut() {
        val json = """
            { "in": { "type": "explode", "corner": { "horizontal": "start", "vertical": "top" } },
              "out": { "type": "fade" } }
        """.trimIndent()

        val animation = ModalAnimation.fromJson(JsonValue.parseString(json))
        assertEquals(ModalAnimationEffect.Type.EXPLODE, animation.enter.type)
        assertEquals(ModalAnimationEffect.Type.FADE, animation.exit.type)
    }

    @Test
    public fun testRoundTrip() {
        val animation = ModalAnimation(
            enter = ModalAnimationEffect.Explode(
                corner = CornerPosition(HorizontalEdge.START, VerticalEdge.BOTTOM),
                duration = 0.6.seconds
            ),
            exit = ModalAnimationEffect.Fade(duration = 0.4.seconds)
        )

        // CornerPosition/EdgePosition aren't data classes, so they compare by
        // reference -- check the fields that actually carry the value instead.
        val roundTripped = ModalAnimation.fromJson(animation.toJsonValue())
        val enter = roundTripped.enter as ModalAnimationEffect.Explode
        val exit = roundTripped.exit as ModalAnimationEffect.Fade
        assertEquals(HorizontalEdge.START, enter.corner.horizontal)
        assertEquals(VerticalEdge.BOTTOM, enter.corner.vertical)
        assertEquals(0.6.seconds, enter.duration)
        assertEquals(0.4.seconds, exit.duration)
    }
}
