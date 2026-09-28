/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.property.GridLayoutConfig
import com.urbanairship.android.layout.property.GridLayoutConfigs
import com.urbanairship.android.layout.property.GridMinColumnWidth
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Orientation
import com.urbanairship.android.layout.property.Platform
import com.urbanairship.android.layout.property.WindowSize
import kotlin.math.floor

/** Slack in the column fit, so a width that fits a whole number of columns exactly still does after rounding. */
private const val COLUMN_FIT_EPSILON = 0.5f

/** What a grid config selector is matched against. */
internal data class GridEnvironment(
    val platform: Platform = Platform.ANDROID,
    val windowSize: WindowSize?,
    val orientation: Orientation?,
    /** The grid's own inner width in points, or null when it has none to measure. */
    val gridWidth: Float?
)

/**
 * Picks the config for a grid: the first selector whose conditions all match, or the default.
 *
 * A selector naming `grid_width` never matches a grid with no width.
 *
 * @param configs the grid's `config`.
 * @param environment what the selectors are matched against.
 * @return the config to lay the grid out with.
 */
internal fun resolveGridConfig(configs: GridLayoutConfigs, environment: GridEnvironment): GridLayoutConfig =
    configs.selectors.firstOrNull { selector ->
        (selector.platform == null || selector.platform == environment.platform.toString()) &&
            (selector.windowSize == null || selector.windowSize == environment.windowSize) &&
            (selector.orientation == null || selector.orientation == environment.orientation) &&
            (selector.gridWidth == null ||
                (environment.gridWidth != null && environment.gridWidth in selector.gridWidth))
    }?.config ?: configs.default

/**
 * Returns a grid's minimum column width in points, after any text scaling the config asks for.
 *
 * @param config the resolved config.
 * @param textScale the accessibility text scale, 1 at the default size.
 * @return the minimum width, or null where the config sets none or a non-positive one.
 */
internal fun gridMinColumnWidth(config: GridLayoutConfig, textScale: Float): Float? {
    val minWidth = config.minColumnWidth?.takeIf { it.points > 0 } ?: return null
    return when (minWidth.type) {
        GridMinColumnWidth.Type.FIXED -> minWidth.points
        GridMinColumnWidth.Type.TEXT_SCALED -> minWidth.points * textScale
    }
}

/**
 * Counts the columns a grid draws at a width.
 *
 * As many columns of at least the minimum as fit, from 1 up to `max_columns`: 170 across at a 50
 * minimum and 10 apart is 3 columns, and so is 169.6, but 169 is 2. Without a minimum, or without a
 * width to fit into, it is `max_columns`.
 *
 * @param config the resolved config.
 * @param width the grid's inner width in points, or null when unbounded.
 * @param textScale the accessibility text scale, 1 at the default size.
 * @return the column count, at least 1.
 */
internal fun gridColumnCount(config: GridLayoutConfig, width: Float?, textScale: Float = 1f): Int {
    val max = config.maxColumns.coerceAtLeast(1)
    val minWidth = gridMinColumnWidth(config, textScale)
    if (minWidth == null || width == null) return max
    val spacing = config.columnSpacing
    val fits = floor((width + spacing + COLUMN_FIT_EPSILON) / (minWidth + spacing)).toInt()
    return fits.coerceIn(1, max)
}

/** Where one item sits in a grid. */
internal data class GridPlacement(
    /** The item's row, from 0. */
    val row: Int,
    /**
     * The half-column the item starts at, from 0, counted from the start edge. Half-columns let a
     * short row be centred: one column short, it starts half a column in.
     */
    val halfColumn: Int,
    /** The whole columns the item covers, from 1 to the column count. */
    val span: Int
)

/**
 * Places items in rows, in order, each covering its span of columns.
 *
 * An item that doesn't fit what is left of its row starts the next one, and no later item goes back
 * to fill the gap, so any row can come up short. A short row is offset by [alignment]: 3 columns
 * holding one item sit it at half-column 0 for start, 2 for center and 4 for end.
 *
 * @param spans each item's requested span, in item order.
 * @param columns the column count, at least 1.
 * @param alignment where a short row sits.
 * @return each item's placement, in item order.
 */
internal fun placeGridItems(
    spans: List<Int>,
    columns: Int,
    alignment: HorizontalPosition
): List<GridPlacement> {
    val rows = mutableListOf<MutableList<Pair<Int, Int>>>()
    var row = mutableListOf<Pair<Int, Int>>()
    var used = 0
    spans.forEachIndexed { index, requested ->
        val span = requested.coerceIn(1, columns)
        if (used + span > columns) {
            rows.add(row)
            row = mutableListOf()
            used = 0
        }
        row.add(index to span)
        used += span
    }
    if (row.isNotEmpty()) rows.add(row)

    val placements = arrayOfNulls<GridPlacement>(spans.size)
    rows.forEachIndexed { rowIndex, cells ->
        val leftover = columns - cells.sumOf { it.second }
        var halfColumn = when (alignment) {
            HorizontalPosition.START -> 0
            HorizontalPosition.CENTER -> leftover
            HorizontalPosition.END -> 2 * leftover
        }
        cells.forEach { (index, span) ->
            placements[index] = GridPlacement(rowIndex, halfColumn, span)
            halfColumn += 2 * span
        }
    }
    return placements.map { requireNotNull(it) }
}
