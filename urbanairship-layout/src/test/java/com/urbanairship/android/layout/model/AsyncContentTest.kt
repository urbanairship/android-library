/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.graphics.Color
import com.urbanairship.android.layout.info.ContainerLayoutInfo
import com.urbanairship.android.layout.info.EmptyInfo
import com.urbanairship.android.layout.property.NamedColors
import com.urbanairship.android.layout.property.NamedColorsContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class AsyncContentTest {

    private val sceneColors = NamedColors.EMPTY.scopedTo(
        JsonValue.parseString(
            """{ "references": { "colors": {
                "ink": { "default": { "type": "hex", "hex": "#111111" } },
                "brand": { "default": { "type": "hex", "hex": "#222222" } }
            } } }"""
        ).optMap()
    )

    private fun parse(json: String): AsyncContent =
        AsyncContent.fromJson(JsonValue.parseString(json).requireMap(), sceneColors)

    private fun contextWith(colors: NamedColors): Context =
        NamedColorsContext.wrap(RuntimeEnvironment.getApplication(), colors)

    @Test
    public fun testABareViewParsesWithTheScenesColors() {
        val content = parse("""{ "type": "empty_view" }""")

        assertTrue(content.info is EmptyInfo)
        assertSame(sceneColors, content.namedColors)
    }

    @Test
    public fun testABareViewsOwnReferencesAreIgnored() {
        val content = parse(
            """
            {
              "type": "empty_view",
              "references": { "colors": { "extra": { "default": { "type": "hex", "hex": "#00FF00" } } } }
            }
            """
        )

        assertTrue(content.info is EmptyInfo)
        assertSame(sceneColors, content.namedColors)
        assertNull(content.namedColors["extra"])
    }

    @Test
    public fun testAWrappersColorsAreLookedUpBeforeTheScenes() {
        val content = parse(
            """
            {
              "view": { "type": "container", "items": [] },
              "references": { "colors": { "brand": { "default": { "type": "hex", "hex": "#C8336C" } } } }
            }
            """
        )

        assertTrue(content.info is ContainerLayoutInfo)
        val context = contextWith(content.namedColors)
        assertEquals(Color.parseColor("#C8336C"), content.namedColors["brand"]?.resolve(context))
        assertEquals(Color.parseColor("#111111"), content.namedColors["ink"]?.resolve(context))
    }

    @Test
    public fun testAWrapperWithoutReferencesKeepsTheScenesColors() {
        val content = parse("""{ "view": { "type": "empty_view" } }""")

        assertTrue(content.info is EmptyInfo)
        assertSame(sceneColors, content.namedColors)
    }

    @Test
    public fun testAWrapperWithABrokenViewFails() {
        assertThrows(JsonException::class.java) { parse("""{ "view": { "type": "not_a_view" } }""") }
        assertThrows(JsonException::class.java) { parse("""{ "view": "empty_view" }""") }
    }

    @Test
    public fun testAWrapperWithMalformedReferencesFails() {
        assertThrows(JsonException::class.java) {
            parse("""{ "view": { "type": "empty_view" }, "references": [] }""")
        }
        assertThrows(JsonException::class.java) {
            parse("""{ "view": { "type": "empty_view" }, "references": { "colors": "brand" } }""")
        }
        assertThrows(JsonException::class.java) {
            parse("""{ "view": { "type": "empty_view" }, "references": { "colors": { "brand": "#C8336C" } } }""")
        }
    }

    @Test
    public fun testAnObjectWithBothViewAndTypeIsAView() {
        val content = parse("""{ "type": "empty_view", "view": { "type": "container", "items": [] } }""")

        assertTrue(content.info is EmptyInfo)
    }
}
