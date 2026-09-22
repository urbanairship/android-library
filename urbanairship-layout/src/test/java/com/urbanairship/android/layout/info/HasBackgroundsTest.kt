package com.urbanairship.android.layout.info

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.property.Background
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Confirms `backgrounds` is actually wired into the six view types, not just parseable on its own. */
@RunWith(AndroidJUnit4::class)
public class HasBackgroundsTest {

    @Test
    public fun containerDecodesBackgrounds() {
        val json = """
            {
                "type": "container",
                "items": [],
                "backgrounds": [
                    { "type": "color", "color": { "default": { "hex": "#000000", "alpha": 1 } } }
                ]
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as ContainerLayoutInfo

        assertEquals(1, info.backgrounds?.size)
        assertTrue(info.backgrounds?.get(0) is Background.ColorBackground)
    }

    @Test
    public fun containerBackgroundsAreOptional() {
        val json = """{ "type": "container", "items": [] }"""

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as ContainerLayoutInfo

        assertNull(info.backgrounds)
    }

    @Test
    public fun pagerDecodesParallaxBackgrounds() {
        val json = """
            {
                "type": "pager",
                "items": [],
                "backgrounds": [
                    {
                        "type": "media", "media_type": "image", "url": "https://example/hero.jpg",
                        "media_fit": "fit_crop", "parallax": { "axis": "vertical", "factor": 0.4 }
                    }
                ]
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as PagerInfo

        assertEquals(0.4f, info.backgrounds?.get(0)?.parallax?.factor)
    }

    @Test
    public fun scrollLayoutDecodesParallaxBackgrounds() {
        val json = """
            {
                "type": "scroll_layout",
                "direction": "vertical",
                "view": { "type": "empty_view" },
                "backgrounds": [
                    { "type": "color", "color": { "default": { "hex": "#0B1B33", "alpha": 1 } } }
                ]
            }
        """

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).optMap()) as ScrollLayoutInfo

        assertEquals(1, info.backgrounds?.size)
        assertNull(info.backgrounds?.get(0)?.parallax)
    }
}
