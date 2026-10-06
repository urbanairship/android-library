/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class BackgroundTest {

    @Test
    public fun testColorBackground() {
        val json = """
            {
              "type": "color",
              "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 1 } },
              "blend_mode": "multiply",
              "opacity": 0.5
            }
        """.trimIndent()

        val background = Background.fromJson(JsonValue.parseString(json).requireMap())
        assertTrue(background is Background.ColorBackground)
        assertEquals(BlendMode.MULTIPLY, background.blendMode)
        assertEquals(0.5f, background.opacity)
    }

    @Test
    public fun testGradientBackground() {
        val json = """
            {
              "type": "gradient",
              "gradient": {
                "style": "linear",
                "angle": 180,
                "stops": [
                  { "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0 } }, "position": 0 },
                  { "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 0.6 } }, "position": 1 }
                ]
              }
            }
        """.trimIndent()

        val background = Background.fromJson(JsonValue.parseString(json).requireMap())
        assertTrue(background is Background.GradientBackground)
        val gradient = (background as Background.GradientBackground).gradient
        assertEquals(Gradient.Style.LINEAR, gradient.style)
        assertEquals(180f, gradient.angle)
        assertEquals(2, gradient.stops.size)
        assertNull(background.blendMode)
        assertNull(background.opacity)
    }

    @Test
    public fun testMediaBackground() {
        val json = """
            {
              "type": "media",
              "media_type": "image",
              "url": "https://test-layout.internal/1200/1200/blue/3",
              "media_fit": "fit_crop"
            }
        """.trimIndent()

        val background = Background.fromJson(JsonValue.parseString(json).requireMap())
        assertTrue(background is Background.MediaBackground)
        val media = background as Background.MediaBackground
        assertEquals(MediaType.IMAGE, media.mediaType)
        assertEquals(MediaFit.FIT_CROP, media.mediaFit)
    }

    @Test
    public fun testAPlayableMediaBackgroundParsesItsOwnMediaInfo() {
        // A playable layer is rendered by a real MediaInfo/MediaModel/MediaView (see
        // videoBackgroundModels), and that info is parsed here with the rest of the layer rather
        // than by keeping the raw json around to re-parse at render time.
        val json = """
            {
              "type": "media",
              "media_type": "video",
              "url": "https://test-layout.internal/video.mp4",
              "media_fit": "fit_crop",
              "video": { "autoplay": true, "muted": true, "loop": true, "show_controls": false }
            }
        """.trimIndent()
        val jsonMap = JsonValue.parseString(json).requireMap()

        val background = Background.fromJson(jsonMap)
        assertTrue(background is Background.MediaBackground.VideoMediaBackground)
        val media = background as Background.MediaBackground.VideoMediaBackground
        assertEquals(MediaType.VIDEO, media.mediaType)
        assertEquals("https://test-layout.internal/video.mp4", media.mediaInfo.url)
        assertEquals(MediaFit.FIT_CROP, media.mediaInfo.mediaFit)
        assertEquals(true, media.video?.autoplay)
    }

    @Test
    public fun testAnImageMediaBackgroundHasNoPlaybackInfoAtAll() {
        val json = """
            {
              "type": "media",
              "media_type": "image",
              "url": "https://test-layout.internal/image.jpg",
              "media_fit": "fit_crop"
            }
        """.trimIndent()

        val background = Background.fromJson(JsonValue.parseString(json).requireMap())

        // Not a VideoMediaBackground, so `video` isn't reachable on it -- an image has nothing
        // for autoplay/mute/loop/controls to mean.
        assertTrue(background is Background.MediaBackground.ImageMediaBackground)
    }

    @Test
    public fun testBackgroundList() {
        val json = """
            [
              { "type": "color", "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 1 } } },
              { "type": "media", "media_type": "image", "url": "https://example/hero.jpg", "media_fit": "fit_crop" }
            ]
        """.trimIndent()

        val backgrounds = Background.fromJsonList(JsonValue.parseString(json).optList())
        assertEquals(2, backgrounds.size)
        assertTrue(backgrounds[0] is Background.ColorBackground)
        assertTrue(backgrounds[1] is Background.MediaBackground)
    }

    // `Background` has no `parallax` field at all -- only `ParallaxBackground` (used by
    // `pager`/`scroll_layout`, see ParallaxBackgroundTest) does. Adding `"parallax": {...}` to any
    // of the JSON fixtures above has no effect either way: `Background.fromJson` never looks for
    // that key, so it's silently unread rather than rejected, which is why the type-level split
    // (a separate `ParallaxBackground` wrapper) is what actually enforces the scoping -- there is
    // no runtime check to assert on here.
}
