/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerTransitionTest {

    @Test
    public fun testFade() {
        val json = """
            { "in": { "type": "fade", "duration_milliseconds": 300 },
              "out": { "type": "fade", "duration_milliseconds": 300 } }
        """.trimIndent()

        val transition = BannerTransition.fromJson(JsonValue.parseString(json))
        assertEquals(BannerTransitionEffect.Fade(0.3.seconds), transition.enter)
        assertEquals(BannerTransitionEffect.Fade(0.3.seconds), transition.exit)
    }

    @Test
    public fun testSlideHasNoEdgeOfItsOwn() {
        val json = """
            { "in": { "type": "slide" }, "out": { "type": "slide" } }
        """.trimIndent()

        val transition = BannerTransition.fromJson(JsonValue.parseString(json))
        assertEquals(BannerTransitionEffect.Slide(), transition.enter)
        assertEquals(BannerTransitionEffect.Slide(), transition.exit)
    }

    @Test
    public fun testDefaultsToSlideBothWays() {
        val transition = BannerTransition.default()
        assertEquals(BannerTransitionEffect.Slide(), transition.enter)
        assertEquals(BannerTransitionEffect.Slide(), transition.exit)
    }

    @Test
    public fun testRoundTrip() {
        val transition = BannerTransition(
            enter = BannerTransitionEffect.Fade(duration = 0.5.seconds),
            exit = BannerTransitionEffect.Slide(duration = 0.25.seconds)
        )

        val roundTripped = BannerTransition.fromJson(transition.toJsonValue())
        assertEquals(transition, roundTripped)
    }
}
