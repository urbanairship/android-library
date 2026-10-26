/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import android.graphics.Color as AndroidColor
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.info.LabelInfo
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Web-thomas's `references.test.tsx` cases, so a named color resolves the same on every platform. */
@RunWith(RobolectricTestRunner::class)
public class NamedColorsTest {

    private val colors = """
        {
          "ink": {
            "default": { "type": "hex", "hex": "#1F1F24", "alpha": 1 },
            "selectors": [{ "dark_mode": true, "color": { "type": "hex", "hex": "#E5E8EE", "alpha": 1 } }]
          },
          "brand": { "default": { "type": "hex", "hex": "#C8336C", "alpha": 1 } }
        }
    """

    private val scene = NamedColors.EMPTY.scopedTo(
        JsonValue.parseString("""{ "references": { "colors": $colors } }""").optMap()
    )

    private fun reference(name: String) = Color.fromJson(
        JsonValue.parseString("""{ "type": "color_reference", "name": "$name" }""")
    )

    private fun contextWith(colors: NamedColors): Context =
        NamedColorsContext.wrap(RuntimeEnvironment.getApplication(), colors)

    @Test
    public fun testAColorGivenInPlaceDrawsItself() {
        val color = Color.fromJson(JsonValue.parseString("""{ "default": { "type": "hex", "hex": "#123456" } }"""))
        assertEquals(AndroidColor.parseColor("#123456"), color.resolve(contextWith(scene)))
    }

    @Test
    public fun testAReferenceDrawsTheNamedColor() {
        assertEquals(AndroidColor.parseColor("#C8336C"), reference("brand").resolve(contextWith(scene)))
    }

    @Test
    public fun testAReferenceSelectsFromTheNamedColorsSelectors() {
        RuntimeEnvironment.setQualifiers("+night")
        assertEquals(AndroidColor.parseColor("#E5E8EE"), reference("ink").resolve(contextWith(scene)))
    }

    @Test
    public fun testAnUnknownNameDrawsOpaqueBlack() {
        assertEquals(AndroidColor.BLACK, reference("missing").resolve(contextWith(scene)))
        assertEquals(AndroidColor.BLACK, reference("ink").resolve(RuntimeEnvironment.getApplication()))
    }

    @Test
    public fun testTheInnermostColorsWinAndFallBackToTheOuter() {
        val content = scene.scopedTo(
            JsonValue.parseString(
                """{ "references": { "colors": { "brand": { "default": { "type": "hex", "hex": "#00FF00" } } } } }"""
            ).optMap()
        )
        val inner = NamedColorsContext.wrap(contextWith(scene), content)

        assertEquals(AndroidColor.GREEN, reference("brand").resolve(inner))
        assertEquals(AndroidColor.parseColor("#1F1F24"), reference("ink").resolve(inner))
        assertEquals(AndroidColor.parseColor("#C8336C"), reference("brand").resolve(contextWith(scene)))
    }

    @Test
    public fun testANamedColorCantBeAReference() {
        assertThrows(JsonException::class.java) {
            NamedColors.EMPTY.scopedTo(
                JsonValue.parseString(
                    """{ "references": { "colors": { "ink": { "type": "color_reference", "name": "ink" } } } }"""
                ).optMap()
            )
        }
    }

    @Test
    public fun testALayoutDrawsItsNamedColorsInTheViewAndPresentation() {
        val layout = LayoutInfo(
            JsonValue.parseString(
                """
                {
                  "version": 1,
                  "references": { "colors": $colors },
                  "presentation": {
                    "type": "modal",
                    "default_placement": {
                      "size": { "width": "100%", "height": "100%" },
                      "shade_color": { "type": "color_reference", "name": "ink" }
                    }
                  },
                  "view": {
                    "type": "label",
                    "text": "Hi",
                    "background_color": { "type": "color_reference", "name": "brand" },
                    "text_appearance": { "font_size": 14, "color": { "type": "color_reference", "name": "ink" } }
                  }
                }
                """
            ).optMap()
        )
        val context = contextWith(layout.namedColors)

        val label = layout.view as LabelInfo
        assertEquals(AndroidColor.parseColor("#C8336C"), label.backgroundColor?.resolve(context))
        assertEquals(AndroidColor.parseColor("#1F1F24"), label.textAppearance.color.resolve(context))
        val placement = (layout.presentation as ModalPresentation).defaultPlacement
        assertEquals(AndroidColor.parseColor("#1F1F24"), placement.shadeColor?.resolve(context))
    }

    @Test
    public fun testALayoutSelectsOnEnvironmentConditionsThroughANamedColor() {
        fun layout(references: String) = LayoutInfo(
            JsonValue.parseString(
                """
                {
                  "version": 1,
                  "references": $references,
                  "presentation": { "type": "modal", "default_placement": { "size": { "width": "100%", "height": "100%" } } },
                  "view": { "type": "empty_view", "background_color": { "type": "color_reference", "name": "tint" } }
                }
                """
            ).optMap()
        )

        val conditional = layout(
            """{ "colors": { "tint": {
                "default": { "type": "hex", "hex": "#000000" },
                "selectors": [{ "environment_conditions": { "theme": "sale" }, "color": { "type": "hex", "hex": "#FF0000" } }]
            } } }"""
        )
        val plain = layout("""{ "colors": { "tint": { "default": { "type": "hex", "hex": "#000000" } } } }""")

        assertTrue(conditional.selectsOnEnvironmentConditions)
        assertFalse(plain.selectsOnEnvironmentConditions)
    }
}
