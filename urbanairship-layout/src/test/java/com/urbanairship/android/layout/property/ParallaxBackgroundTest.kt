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
public class ParallaxBackgroundTest {

    @Test
    public fun testWithParallax() {
        val json = """
            {
              "type": "media",
              "media_type": "image",
              "url": "https://example/hero.jpg",
              "media_fit": "fit_crop",
              "parallax": { "factor": 0.4 }
            }
        """.trimIndent()

        val layer = ParallaxBackground.fromJson(JsonValue.parseString(json).requireMap())
        assertTrue(layer.background is Background.MediaBackground)
        assertEquals(0.4f, layer.parallax?.factor)
    }

    @Test
    public fun testWithoutParallax() {
        val json = """
            {
              "type": "color",
              "color": { "default": { "type": "hex", "hex": "#000000", "alpha": 1 } }
            }
        """.trimIndent()

        val layer = ParallaxBackground.fromJson(JsonValue.parseString(json).requireMap())
        assertNull(layer.parallax)
    }

    @Test
    public fun testList() {
        val json = """
            [
              { "type": "color", "color": { "default": { "type": "hex", "hex": "#0B1B33", "alpha": 1 } } },
              {
                "type": "media", "media_type": "image", "url": "https://example/mid.jpg",
                "media_fit": "fit_crop", "parallax": { "factor": 0.5 }
              }
            ]
        """.trimIndent()

        val layers = ParallaxBackground.fromJsonList(JsonValue.parseString(json).optList())
        assertEquals(2, layers.size)
        assertNull(layers[0].parallax)
        assertEquals(0.5f, layers[1].parallax?.factor)
    }
}
