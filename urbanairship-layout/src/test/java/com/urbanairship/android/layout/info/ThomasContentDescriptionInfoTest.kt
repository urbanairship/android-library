/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.info

import com.urbanairship.ai.EvaluationContext
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class ThomasContentDescriptionInfoTest {

    @Test
    public fun testDescribesTheContentAndTheUserSeparately() {
        val info = parse(
            """
            {
                "description": "Spring sale on cat trees",
                "additional_context": [
                    { "content": "Interests: cats", "priority": -5 },
                    { "content": "Targeted: lapsed buyer" }
                ]
            }
            """
        )

        assertEquals("Spring sale on cat trees", info.description)
        assertEquals(
            listOf(
                EvaluationContext.Item("Interests: cats", -5.0),
                EvaluationContext.Item("Targeted: lapsed buyer", 0.0)
            ),
            info.additionalContext
        )
    }

    @Test
    public fun testBothHalvesAreOptional() {
        val info = parse("{}")

        assertNull(info.description)
        assertTrue(info.additionalContext.isEmpty())
    }

    /** The whole of content_description is advisory, so a bad entry costs only itself. */
    @Test
    public fun testMalformedContextItemsAreDropped() {
        val info = parse(
            """
            {
                "description": "Spring sale on cat trees",
                "additional_context": [
                    { "content": "Interests: cats" },
                    { "priority": -5 },
                    "not an object",
                    { "content": "Targeted: lapsed buyer" }
                ]
            }
            """
        )

        assertEquals("Spring sale on cat trees", info.description)
        assertEquals(
            listOf(
                EvaluationContext.Item("Interests: cats"),
                EvaluationContext.Item("Targeted: lapsed buyer")
            ),
            info.additionalContext
        )
    }

    /**
     * This parses inside `LayoutInfo(json)`, so a throw would cost the layout its ability to
     * render rather than its description.
     */
    @Test
    public fun testAMalformedContextItemDoesNotFailTheLayout() {
        val layout = LayoutInfo(
            JsonValue.parseString(
                """
                {
                    "version": 1,
                    "presentation": {
                        "type": "embedded",
                        "embedded_id": "home_banner",
                        "default_placement": { "size": { "width": "100%", "height": "auto" } }
                    },
                    "view": { "type": "empty_view" },
                    "content_description": {
                        "description": "Spring sale on cat trees",
                        "additional_context": [{ "priority": -5 }]
                    }
                }
                """
            ).requireMap()
        )

        assertEquals("Spring sale on cat trees", layout.contentDescription?.description)
        assertTrue(layout.contentDescription?.additionalContext?.isEmpty() == true)
    }

    @Test
    public fun testReadFromTheLayout() {
        val layout = LayoutInfo(
            JsonValue.parseString(
                """
                {
                    "version": 1,
                    "presentation": {
                        "type": "embedded",
                        "embedded_id": "home_banner",
                        "default_placement": { "size": { "width": "100%", "height": "auto" } }
                    },
                    "view": { "type": "empty_view" },
                    "content_description": {
                        "description": "Spring sale on cat trees",
                        "additional_context": [{ "content": "Interests: cats" }]
                    }
                }
                """
            ).requireMap()
        )

        assertEquals("Spring sale on cat trees", layout.contentDescription?.description)
        assertEquals(
            listOf(EvaluationContext.Item("Interests: cats")),
            layout.contentDescription?.additionalContext
        )
    }

    @Test
    public fun testAbsentFromTheLayout() {
        val layout = LayoutInfo(
            JsonValue.parseString(
                """
                {
                    "version": 1,
                    "presentation": {
                        "type": "embedded",
                        "embedded_id": "home_banner",
                        "default_placement": { "size": { "width": "100%", "height": "auto" } }
                    },
                    "view": { "type": "empty_view" }
                }
                """
            ).requireMap()
        )

        assertNull(layout.contentDescription)
    }

    private fun parse(json: String): ThomasContentDescriptionInfo =
        ThomasContentDescriptionInfo.fromJson(JsonValue.parseString(json).requireMap())
}
