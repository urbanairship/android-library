/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.info.ViewPropertyOverride
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.JsonMap
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

    /** A predicate on the grid's `size` condition. */
    private fun gridSize(value: String) = JsonPredicate.parse(
        JsonValue.parseString("""{"scope": ["${'$'}grids", "current", "conditions", "size"], "value": {"equals": "$value"}}""")
    )

    private val overrides = listOf(
        ViewPropertyOverride(gridSize("narrow"), "narrow"),
        ViewPropertyOverride(gridSize("wide"), "wide")
    )

    private fun resolve(conditions: JsonMap) =
        state.copy(grid = GridState("grid", conditions)).resolveRequired(overrides, "default")

    @Test
    public fun testTakesTheOverrideThatMatchesTheGridsCondition() {
        assertEquals("narrow", resolve(jsonMapOf("size" to "narrow")))
        assertEquals("wide", resolve(jsonMapOf("size" to "wide")))
    }

    @Test
    public fun testAConditionNoSelectorChoseMatchesNoPredicateOnIt() {
        assertEquals("default", resolve(JsonMap.EMPTY_MAP))
    }

    @Test
    public fun testOnlyAGridsStateHasGrids() {
        assertFalse(state.toJsonValue().optMap().containsKey("${'$'}grids"))
        assertEquals("default", state.resolveRequired(overrides, "default"))
    }
}
