/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.ThomasModelFactory
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.GridLayoutModel
import com.urbanairship.android.layout.model.LinearLayoutModel
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class GridStateScopeTest {

    private fun environment(scope: CoroutineScope) = ModelEnvironment(
        layoutState = LayoutState.EMPTY,
        reporter = mockk(relaxUnitFun = true),
        actionsRunner = mockk(relaxUnitFun = true),
        displayTimer = mockk(relaxed = true),
        modelScope = scope
    )

    private fun build(json: String, environment: ModelEnvironment) =
        ThomasModelFactory().create(ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap()), environment)

    private fun ModelEnvironment.scopeOf(grid: String?) = stateScopes.scopes.single { it.value.grid?.identifier == grid }

    private fun ThomasState.gridSize(): JsonValue =
        toJsonValue().optMap().opt("\$grids").optMap().opt("current").optMap().opt("conditions").optMap().opt("size")

    @Test
    public fun `a grid and everything inside it see the nearest grid`(): TestResult = runTest {
        val environment = environment(backgroundScope)
        val root = build(SCENE, environment) as LinearLayoutModel
        val outer = root.items[1].model as GridLayoutModel
        val inner = outer.items[1].model as GridLayoutModel

        // The view outside, the outer grid with its header and first cell, and the nested grid with its cell.
        assertEquals(3, environment.stateScopes.scopes.size)
        assertSame(outer.gridState.changes.value, environment.scopeOf("outer").value.grid)
        assertSame(inner.gridState.changes.value, environment.scopeOf("inner").value.grid)

        outer.gridState.update { it.copy(conditions = jsonMapOf("size" to "wide")) }
        inner.gridState.update { it.copy(conditions = jsonMapOf("size" to "narrow")) }

        assertEquals(JsonValue.wrap("wide"), environment.scopeOf("outer").value.gridSize())
        assertEquals(JsonValue.wrap("narrow"), environment.scopeOf("inner").value.gridSize())
        assertFalse(environment.scopeOf(null).value.toJsonValue().optMap().containsKey("\$grids"))
    }

    @Test
    public fun `a grid with no conditions declared has none`(): TestResult = runTest {
        val environment = environment(backgroundScope)
        build(SCENE, environment)

        assertEquals(JsonValue.NULL, environment.scopeOf("outer").value.gridSize())
    }

    @Test
    public fun `async content inside a grid sees the grid`(): TestResult = runTest {
        val environment = environment(backgroundScope)
        val grid = SharedState(GridState("outer", conditions = jsonMapOf("size" to "wide")))
        val parent = LayoutState(
            pager = null, checkbox = null, radio = null, score = null, asyncView = null,
            layout = LayoutState.EMPTY.layout, video = null, videoControl = null,
            thomasState = LayoutState.EMPTY.thomasState, thomasForm = null, parentForm = null,
            pagerTracker = null, grid = grid
        )

        ThomasModelFactory().create(
            ViewInfo.viewInfoFromJson(JsonValue.parseString(LABEL).requireMap()),
            environment,
            parent,
            environment.stateScopes.owner()
        )

        assertEquals(JsonValue.wrap("wide"), environment.scopeOf("outer").value.gridSize())
    }

    private companion object {
        val LABEL = """
            {"type": "label", "text": "x",
             "text_appearance": {"font_size": 14, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}}
        """.trimIndent()

        val SCENE = """
            {"type": "linear_layout", "direction": "vertical", "items": [
              {"size": {"width": "100%", "height": "auto"}, "view": $LABEL},
              {"size": {"width": "100%", "height": "auto"}, "view": {
                "type": "grid_layout", "identifier": "outer",
                "config": {"columns": {"type": "fixed", "count": 2}},
                "header": {"view": $LABEL},
                "items": [
                  {"view": $LABEL},
                  {"view": {
                    "type": "grid_layout", "identifier": "inner",
                    "config": {"columns": {"type": "fixed", "count": 1}},
                    "items": [{"view": $LABEL}]
                  }}
                ]
              }}
            ]}
        """.trimIndent()
    }
}
