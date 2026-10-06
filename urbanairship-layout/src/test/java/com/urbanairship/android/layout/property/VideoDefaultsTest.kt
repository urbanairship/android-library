/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What a `video` block means when it leaves a field out.
 *
 * These defaults are a cross-platform contract, not an Android choice: the same block has to
 * behave the same way on every platform, and the same way whether it sits on an in-flow `media`
 * view or on a `backgrounds` layer. Web resolves each absent field to its falsy value
 * (`Backgrounds.tsx`, `MediaVideoPrensenter.tsx`), and these match it.
 *
 * `show_controls` is the one worth stating out loud: a video is decorative unless a scene asks
 * for a player, so leaving it out means no controls. Android used to default it to `true`, which
 * put controls on a video that never asked for them and, on a `backgrounds` layer, let the
 * WebView swallow taps meant for the host.
 */
@RunWith(RobolectricTestRunner::class)
public class VideoDefaultsTest {

    @Test
    public fun anEmptyVideoBlockIsADecorativeVideo() {
        val video = Video.fromJson(JsonValue.parseString("{}").requireMap())

        assertFalse("a video shows no controls unless asked", video.showControls)
        assertFalse(video.autoplay)
        assertFalse(video.muted)
        assertFalse(video.loop)
        assertNull(video.aspectRatio)
    }

    @Test
    public fun statedFieldsAreHonoured() {
        val video = Video.fromJson(
            JsonValue.parseString(
                """{ "show_controls": true, "autoplay": true, "muted": true, "loop": true }"""
            ).requireMap()
        )

        assertTrue(video.showControls)
        assertTrue(video.autoplay)
        assertTrue(video.muted)
        assertTrue(video.loop)
    }

    /**
     * `auto_reset_position` follows from the other two rather than having a default of its own: a
     * video that plays by itself and offers no way to scrub is decorative, so it starts over
     * rather than resuming half-watched. A scene can always say otherwise.
     */
    @Test
    public fun autoResetPositionFollowsFromAutoplayAndControls() {
        fun resetFor(json: String) =
            Video.fromJson(JsonValue.parseString(json).requireMap()).autoResetPosition

        assertTrue("autoplaying with no controls is decorative", resetFor("""{ "autoplay": true }"""))
        assertFalse(
            "a player the viewer drives should keep its position",
            resetFor("""{ "autoplay": true, "show_controls": true }""")
        )
        assertFalse("nothing to reset if it never started itself", resetFor("{}"))
        assertFalse(
            "and a scene can always say otherwise",
            resetFor("""{ "autoplay": true, "auto_reset_position": false }""")
        )
    }
}
