/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.info.ViewPropertyOverride
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ThomasStateGridTest {

    private val state = ThomasState(
        layout = State.Layout(identifier = "layout"),
        form = null,
        pager = null,
        video = null,
        asyncView = null,
        capabilities = ThomasCapabilities.NONE
    )

    private fun gridWidth(range: String) = JsonPredicate.parse(
        JsonValue.parseString("""{"scope": ["${'$'}grids", "current", "width"], "value": $range}""")
    )

    private val overrides = listOf(
        ViewPropertyOverride(gridWidth("""{"at_most": 300}"""), "narrow"),
        ViewPropertyOverride(gridWidth("""{"at_least": 300}"""), "wide")
    )

    private fun resolve(width: Float?) = state.copy(grid = GridState("grid", width)).resolveRequired(overrides, "default")

    @Test
    public fun testTakesTheFirstOverrideThatMatchesTheGridsWidth() {
        assertEquals("narrow", resolve(200f))
        assertEquals("wide", resolve(400f))
    }

    @Test
    public fun testGivesASharedBoundaryToTheFirstOverrideListed() {
        assertEquals("narrow", resolve(300f))
    }

    @Test
    public fun testAGridWithNoWidthMatchesNoWidthPredicate() {
        assertEquals("default", resolve(null))
    }

    @Test
    public fun testOnlyAGridsStateHasGrids() {
        assertFalse(state.toJsonValue().optMap().containsKey("${'$'}grids"))
        assertEquals("default", state.resolveRequired(overrides, "default"))
    }
}
