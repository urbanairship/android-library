/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.environment.PredicateEnvironment
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.environment.ThomasEnvironment
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Web-thomas's `conditions.test.ts` cases, so a scene's breakpoints choose the same values everywhere. */
@RunWith(RobolectricTestRunner::class)
public class ConditionsTest {

    private fun measured(
        width: Double = 390.0,
        height: Double = 844.0,
        safeWidth: Double = width,
        safeHeight: Double = height,
        frameWidth: Double? = width,
        frameHeight: Double? = height
    ) = ThomasEnvironment(
        darkMode = false,
        textScale = 1.0,
        windowWidth = width,
        windowHeight = height,
        windowSize = WindowSize.SMALL,
        safeWindowWidth = safeWidth,
        safeWindowHeight = safeHeight,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    private fun environment(conditions: String): EnvironmentConditions =
        JsonValue.parseString("""{"environment_state": {"conditions": $conditions}}""").optMap()
            .conditionsIn("environment_state", EnvironmentConditionSelector::fromJson)

    private fun grid(conditions: String): GridConditions =
        JsonValue.parseString("""{"grid_state": {"conditions": $conditions}}""").optMap()
            .conditionsIn("grid_state", GridConditionSelector::fromJson)

    private val size = environment(
        """
        {"size": [
          {"window_width": {"at_most": 599}, "value": "compact"},
          {"window_width": {"at_most": 1023}, "value": "medium"},
          {"value": "expanded"}
        ]}
        """.trimIndent()
    )

    @Test
    public fun testChoosesABucketByWindowWidthWithInclusiveEnds() {
        listOf(390.0 to "compact", 599.0 to "compact", 600.0 to "medium", 1023.0 to "medium", 1024.0 to "expanded")
            .forEach { (width, expected) ->
                assertEquals("$width wide", jsonMapOf("size" to expected), size.choose(measured(width = width)))
            }
    }

    @Test
    public fun testLeavesOutAConditionNothingMatches() {
        val wide = environment("""{"wide": [{"window_width": {"at_least": 1000}, "value": true}]}""")
        assertEquals(JsonMap.EMPTY_MAP, wide.choose(measured()))
    }

    @Test
    public fun testMatchesNoRangeOnALengthItDoesntKnow() {
        val short = environment("""{"short": [{"frame_height": {"at_most": 400}, "value": true}, {"value": false}]}""")
        // A frame its content sizes has no height.
        assertEquals(jsonMapOf("short" to false), short.choose(measured(frameWidth = 320.0, frameHeight = null)))
    }

    @Test
    public fun testReadsTheSafeWindowAndFrameNotJustTheWindow() {
        val conditions = environment(
            """
            {"safe": [{"safe_window_width": {"at_most": 350}, "value": "inset"}],
             "frame": [{"frame_height": {"at_most": 500}, "value": "short"}]}
            """.trimIndent()
        )
        val inset = measured(safeWidth = 350.0, safeHeight = 800.0, frameWidth = 300.0, frameHeight = 500.0)
        assertEquals(jsonMapOf("safe" to "inset", "frame" to "short"), conditions.choose(inset))
    }

    @Test
    public fun testCombinesSizesWithTheSharedSelectorFields() {
        val shape = environment(
            """
            {"shape": [
              {"orientation": "landscape", "window_height": {"at_most": 500}, "value": "short_landscape"},
              {"value": "regular"}
            ]}
            """.trimIndent()
        )
        assertEquals(jsonMapOf("shape" to "short_landscape"), shape.choose(measured(width = 800.0, height = 400.0)))
        assertEquals(jsonMapOf("shape" to "regular"), shape.choose(measured(width = 400.0, height = 450.0)))
    }

    @Test
    public fun testReturnsNoValuesForASceneWithNoConditions() {
        assertEquals(JsonMap.EMPTY_MAP, Conditions<EnvironmentConditionSelector>(emptyMap()).choose(measured()))
    }

    @Test
    public fun testAGridChoosesFromItsWidth() {
        val layout = grid("""{"layout": [{"width": {"at_most": 360}, "value": "stacked"}, {"value": "columns"}]}""")
        assertEquals(jsonMapOf("layout" to "stacked"), layout.choose(360.0))
        assertEquals(jsonMapOf("layout" to "columns"), layout.choose(361.0))
    }

    @Test
    public fun testAGridWithNoWidthToMeasureFallsThrough() {
        val layout = grid("""{"layout": [{"width": {"at_most": 360}, "value": "stacked"}, {"value": "columns"}]}""")
        assertEquals(jsonMapOf("layout" to "columns"), layout.choose(null))
    }

    @Test
    public fun testSelectorsMatchEnvironmentConditionsByValue() {
        val chosen = JsonValue.parseString("""{"size": "compact", "wide": false, "tag": {"a": 1}}""").optMap()
        val environment = SelectorEnvironment(
            darkMode = false, windowSize = WindowSize.SMALL, orientation = Orientation.PORTRAIT, conditions = chosen
        )
        listOf(
            """{"size": "compact"}""" to true,
            """{"size": "medium"}""" to false,
            """{"wide": false}""" to true,
            """{"tag": {"a": 1}}""" to true,
            """{"size": "compact", "wide": true}""" to false,
            """{"missing": "x"}""" to false
        ).forEach { (named, expected) ->
            val selector = SelectorConditions.fromJson(JsonValue.parseString("""{"environment_conditions": $named}""").optMap())
            assertEquals(named, expected, selector.matches(environment))
        }
    }

    @Test
    public fun testASelectorNamingConditionsNeverMatchesWithoutAnyChosen() {
        val selector = SelectorConditions.fromJson(JsonValue.parseString("""{"environment_conditions": {"size": "compact"}}""").optMap())
        val none = SelectorEnvironment(darkMode = false, windowSize = WindowSize.SMALL, orientation = Orientation.PORTRAIT)
        assertFalse(selector.matches(none))
    }

    @Test
    public fun testAPlacementColorOnAConditionCountsAsSelectingOnConditions() {
        val scene = JsonValue.parseString(
            """
            {"version": 1,
             "presentation": {"type": "modal", "default_placement": {"size": {"width": "100%", "height": "auto"},
               "shade_color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1},
                 "selectors": [{"environment_conditions": {"size": "compact"},
                                "color": {"type": "hex", "hex": "#FF0000", "alpha": 1}}]}}},
             "view": {"type": "empty_view"}}
            """.trimIndent()
        ).optMap()
        assertTrue(LayoutInfo(scene).viewSelectsOnEnvironmentConditions)
    }

    @Test
    public fun testThePredicateEnvironmentKeepsTheRarelyChangingValuesAndTheConditionsAndNoSizes() {
        val json = PredicateEnvironment.of(measured(), jsonMapOf("size" to "compact")).toJsonValue().optMap()
        assertEquals(jsonMapOf("orientation" to "portrait", "size" to "small"), json.opt("window").optMap())
        assertEquals(jsonMapOf("size" to "compact"), json.opt("conditions").optMap())
        assertFalse(json.containsKey("safe_window"))
        assertFalse(json.containsKey("frame"))
    }
}
