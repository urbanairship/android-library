/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import kotlin.time.Duration.Companion.seconds
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BannerAnimationTest {

    @Test
    public fun testFade() {
        val json = """
            { "in": { "type": "fade", "duration_seconds": 0.3 },
              "out": { "type": "fade", "duration_seconds": 0.3 } }
        """.trimIndent()

        val animation = BannerAnimation.fromJson(JsonValue.parseString(json))
        assertEquals(BannerAnimationEffect.Fade(0.3.seconds), animation.enter)
        assertEquals(BannerAnimationEffect.Fade(0.3.seconds), animation.exit)
    }

    @Test
    public fun testSlideHasNoEdgeOfItsOwn() {
        val json = """
            { "in": { "type": "slide" }, "out": { "type": "slide" } }
        """.trimIndent()

        val animation = BannerAnimation.fromJson(JsonValue.parseString(json))
        assertEquals(BannerAnimationEffect.Slide(), animation.enter)
        assertEquals(BannerAnimationEffect.Slide(), animation.exit)
    }

    @Test
    public fun testDefaultsToSlideBothWays() {
        val animation = BannerAnimation.default()
        assertEquals(BannerAnimationEffect.Slide(), animation.enter)
        assertEquals(BannerAnimationEffect.Slide(), animation.exit)
    }

    @Test
    public fun testRoundTrip() {
        val animation = BannerAnimation(
            enter = BannerAnimationEffect.Fade(duration = 0.5.seconds),
            exit = BannerAnimationEffect.Slide(duration = 0.25.seconds)
        )

        val roundTripped = BannerAnimation.fromJson(animation.toJsonValue())
        assertEquals(animation, roundTripped)
    }
}
