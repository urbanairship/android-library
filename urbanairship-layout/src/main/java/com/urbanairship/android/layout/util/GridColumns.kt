/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.property.GridColumns
import com.urbanairship.android.layout.property.GridLayoutConfig
import com.urbanairship.android.layout.property.GridColumnWidth
import com.urbanairship.android.layout.property.GridSpacing
import com.urbanairship.android.layout.property.HorizontalPosition
import kotlin.math.floor

/** Slack in the column fit, so a width that fits a whole number of columns exactly still does after rounding. */
private const val COLUMN_FIT_EPSILON = 0.5f

/**
 * Returns a grid's minimum column width in points, after any text scaling the config asks for.
 *
 * @param config the resolved config.
 * @param textScale the accessibility text scale, 1 at the default size.
 * @return the minimum width, or null for `fixed` columns or a minimum of 0 or less.
 */
internal fun gridMinColumnWidth(config: GridLayoutConfig, textScale: Float): Float? {
    val columns = config.columns as? GridColumns.Fit ?: return null
    return columns.minWidth.scaled(textScale).takeIf { it > 0 }
}

/**
 * Returns the widest a grid's columns grow, in points, after any text scaling it asks for.
 *
 * Never below the minimum, so a `max_width` under `min_width` counts as `min_width`.
 *
 * @param config the resolved config.
 * @param textScale the accessibility text scale, 1 at the default size.
 * @return the cap, or null where columns grow to fill the grid.
 */
internal fun gridMaxColumnWidth(config: GridLayoutConfig, textScale: Float): Float? {
    val columns = config.columns as? GridColumns.Fit ?: return null
    val maxWidth = columns.maxWidth?.scaled(textScale)?.takeIf { it > 0 } ?: return null
    return maxOf(maxWidth, gridMinColumnWidth(config, textScale) ?: 0f)
}

/** This bound, scaled with the text size when it asks to be. */
private fun GridColumnWidth.scaled(textScale: Float): Float = when (type) {
    GridColumnWidth.Type.FIXED -> size
    GridColumnWidth.Type.TEXT_SCALED -> size * textScale
}

/**
 * Returns a [GridSpacing.Spread] gap: the room left over shared between the gaps, within its bounds.
 *
 * 84 left over by 4 columns makes 3 gaps of 28; with a `max_space` of 20 they stop at 20, and
 * `content_position` places the 24 still over.
 *
 * @param spacing a grid's column or row spacing.
 * @param leftover the room the columns or rows leave, before any gaps.
 * @param count how many columns or rows share it.
 * @return the gap, or null for fixed spacing or fewer than two to spread between.
 */
internal fun spreadSpace(spacing: GridSpacing, leftover: Float, count: Int): Float? {
    if (spacing !is GridSpacing.Spread || count < 2) return null
    val max = spacing.maxSpace?.coerceAtLeast(spacing.min) ?: Float.POSITIVE_INFINITY
    return (leftover / (count - 1)).coerceIn(spacing.min, max)
}

/**
 * Counts the columns a grid draws at a width.
 *
 * `fixed` is always its count. `fit` is as many columns of at least the minimum as fit, from 1 up
 * to `max`: 170 across at a 50 minimum and 10 apart is 3 columns, and so is 169.6, but 169 is 2.
 * Without a width to fit into, or with a minimum of 0 or less, it is `max`.
 *
 * @param config the resolved config.
 * @param width the grid's inner width in points, or null when unbounded.
 * @param textScale the accessibility text scale, 1 at the default size.
 * @return the column count, at least 1.
 */
internal fun gridColumnCount(config: GridLayoutConfig, width: Float?, textScale: Float = 1f): Int {
    val max = when (val columns = config.columns) {
        is GridColumns.Fixed -> columns.count
        is GridColumns.Fit -> columns.max
    }.coerceAtLeast(1)
    val minWidth = gridMinColumnWidth(config, textScale)
    if (minWidth == null || width == null) return max
    val spacing = config.columnSpacing.min
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

/** What one item asks of placement. */
internal data class GridItemRequest(
    /** The columns it covers; capped between 1 and the column count. */
    val span: Int,
    /** The column it starts at, from 0 at the start edge, or null for the next free one. */
    val start: Int? = null
)

/**
 * Places items in rows, in order, each covering its span of columns.
 *
 * An item without a start goes at the next free column, or starts the next row if it doesn't fit
 * what is left; no later item goes back to fill a gap, so any row can come up short. A short row is
 * offset by [alignment]: 3 columns holding one item sit it at half-column 0 for start, 2 for center
 * and 4 for end.
 *
 * An item with a start goes at that column, clamped so its span fits: in a 12-column grid, an
 * 8-wide item at start 2 covers columns 2 to 9. A start behind the next free column begins a new
 * row, and the columns it skips stay empty. A row holding any item with a start is drawn as
 * placed, ignoring [alignment].
 *
 * @param items each item's span and start, in item order.
 * @param columns the column count, at least 1.
 * @param alignment where a short row sits.
 * @return each item's placement, in item order.
 */
internal fun placeGridItems(
    items: List<GridItemRequest>,
    columns: Int,
    alignment: HorizontalPosition
): List<GridPlacement> {
    data class Cell(val index: Int, val span: Int, val column: Int)
    data class Row(val cells: List<Cell>, val positioned: Boolean)

    val rows = mutableListOf<Row>()
    var row = mutableListOf<Cell>()
    var positioned = false
    var cursor = 0
    fun nextRow() {
        rows.add(Row(row, positioned))
        row = mutableListOf()
        positioned = false
        cursor = 0
    }

    items.forEachIndexed { index, item ->
        val span = item.span.coerceIn(1, columns)
        if (item.start == null) {
            if (cursor + span > columns) nextRow()
            row.add(Cell(index, span, cursor))
        } else {
            val start = item.start.coerceIn(0, columns - span)
            if (start < cursor) nextRow()
            row.add(Cell(index, span, start))
            positioned = true
            cursor = start
        }
        cursor += span
    }
    if (row.isNotEmpty()) nextRow()

    val placements = arrayOfNulls<GridPlacement>(items.size)
    rows.forEachIndexed { rowIndex, (cells, isPositioned) ->
        val leftover = columns - cells.sumOf { it.span }
        val offset = when {
            isPositioned -> 0
            alignment == HorizontalPosition.CENTER -> leftover
            alignment == HorizontalPosition.END -> 2 * leftover
            else -> 0
        }
        cells.forEach { (index, span, column) ->
            placements[index] = GridPlacement(rowIndex, offset + 2 * column, span)
        }
    }
    return placements.map { requireNotNull(it) }
}
