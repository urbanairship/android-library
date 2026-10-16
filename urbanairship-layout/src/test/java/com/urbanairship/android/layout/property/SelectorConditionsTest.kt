/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Web-thomas's `selectors.test.ts` cases, so every selector matches the same fields on every platform. */
@RunWith(RobolectricTestRunner::class)
public class SelectorConditionsTest {

    private val environment = SelectorEnvironment(
        darkMode = false,
        windowSize = WindowSize.SMALL,
        orientation = Orientation.PORTRAIT
    )

    private fun conditions(json: String) = SelectorConditions.fromJson(JsonValue.parseString(json).optMap())

    @Test
    public fun testASelectorNamingNothingMatchesAnything() {
        assertTrue(conditions("{}").matches(environment))
    }

    @Test
    public fun testEachFieldMatchesAndDoesnt() {
        listOf(
            """{"platform": "android"}""" to true,
            """{"platform": "ios"}""" to false,
            """{"dark_mode": false}""" to true,
            """{"dark_mode": true}""" to false,
            """{"window_size": "small"}""" to true,
            """{"window_size": "large"}""" to false,
            """{"orientation": "portrait"}""" to true,
            """{"orientation": "landscape"}""" to false
        ).forEach { (selector, expected) ->
            assertEquals(selector, expected, conditions(selector).matches(environment))
        }
    }

    @Test
    public fun testEveryNamedFieldHasToHold() {
        assertTrue(conditions("""{"platform": "android", "window_size": "small"}""").matches(environment))
        assertFalse(conditions("""{"platform": "android", "window_size": "large"}""").matches(environment))
    }

    @Test
    public fun testAnUnknownOrientationOrSizeMatchesNoSelectorNamingOne() {
        val unknown = environment.copy(windowSize = null, orientation = null)
        assertFalse(conditions("""{"orientation": "portrait"}""").matches(unknown))
        assertFalse(conditions("""{"window_size": "small"}""").matches(unknown))
        assertTrue(conditions("{}").matches(unknown))
    }

    @Test
    public fun testAColorFollowsTheSizeClassAndTheOrientation() {
        val color = color(
            """{"window_size": "large", "color": ${hex("#111111")}}""",
            """{"orientation": "landscape", "color": ${hex("#222222")}}"""
        )
        assertEquals(0xFF111111.toInt(), color.resolve(context("xlarge-port")))
        assertEquals(0xFF222222.toInt(), color.resolve(context("normal-land")))
        assertEquals(0xFF000000.toInt(), color.resolve(context("normal-port")))
    }

    @Test
    public fun testAColorSelectorWithoutDarkModeMatchesEitherMode() {
        val color = color("""{"platform": "android", "color": ${hex("#111111")}}""")
        assertEquals(0xFF111111.toInt(), color.resolve(context("notnight")))
        assertEquals(0xFF111111.toInt(), color.resolve(context("night")))
    }

    @Test
    public fun testAMediaUrlFollowsTheSizeClassAndThePlatformTogether() {
        val selectors = MediaUrlSelector.fromJsonList(
            JsonValue.parseString("""[{"platform": "android", "window_size": "large", "url": "wide.png"}]""").optList()
        )
        assertEquals("wide.png", MediaUrlSelector.resolve("default.png", selectors, environment.copy(windowSize = WindowSize.LARGE)))
        assertEquals("default.png", MediaUrlSelector.resolve("default.png", selectors, environment))
    }

    @Test
    public fun testAPlacementSelectorNamingAPlatformOrDarkModeAppliesOnlyThere() {
        val presentation = ModalPresentation.fromJson(
            JsonValue.parseString(
                """
                {"type": "modal", "default_placement": $PLACEMENT,
                 "placement_selectors": [
                   {"platform": "ios", "placement": $PLACEMENT},
                   {"dark_mode": true, "placement": $PLACEMENT}
                 ]}
                """.trimIndent()
            )
        )
        val (ios, dark) = presentation.placementSelectors!!.map { it.placement }
        assertSame(dark, presentation.getResolvedPlacement(context("night")))
        assertSame(presentation.defaultPlacement, presentation.getResolvedPlacement(context("notnight")))
        assertFalse(presentation.getResolvedPlacement(context("night")) === ios)
    }

    private fun context(qualifiers: String): Context {
        RuntimeEnvironment.setQualifiers(qualifiers)
        return ApplicationProvider.getApplicationContext()
    }

    private fun color(vararg selectors: String) = Color.fromJson(
        JsonValue.parseString("""{"default": ${hex("#000000")}, "selectors": [${selectors.joinToString()}]}""")
    )

    private fun hex(value: String) = """{"type": "hex", "hex": "$value", "alpha": 1}"""

    private companion object {
        const val PLACEMENT = """{"size": {"width": "80%", "height": "auto"}}"""
    }
}
