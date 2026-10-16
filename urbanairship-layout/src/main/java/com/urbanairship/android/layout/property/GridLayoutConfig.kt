/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/** How a grid divides its width into columns and places its rows. */
internal data class GridLayoutConfig(
    val columns: GridColumns,
    /** The space between columns. */
    val columnSpacing: GridSpacing = GridSpacing.Fixed(0f),
    /** The space between rows. */
    val rowSpacing: GridSpacing = GridSpacing.Fixed(0f),
    /** Where a row that fills fewer columns than the grid has sits. Its cells keep their widths. */
    val partialRowPosition: HorizontalPosition = HorizontalPosition.START,
    /**
     * Where the block of cells sits when the grid is bigger than it: wider, once `max_width` stops
     * the columns growing, or taller than its rows.
     */
    val contentPosition: Position = DEFAULT_CONTENT_POSITION,
    /**
     * Width divided by height for every cell, or null for rows as tall as their tallest item, or
     * as [percentHeightBasis] pools them.
     */
    val cellAspectRatio: Double? = null,
    /** What a cell's percentage height is a share of. No effect with [cellAspectRatio]. */
    val percentHeightBasis: PercentHeightBasis = PercentHeightBasis.ROW
) {

    companion object {
        private val DEFAULT_CONTENT_POSITION = Position(HorizontalPosition.START, VerticalPosition.TOP)

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridLayoutConfig = GridLayoutConfig(
            columns = GridColumns.fromJson(json.requireField("columns")),
            columnSpacing = json.optionalMap("column_spacing")?.let(GridSpacing::fromJson) ?: GridSpacing.Fixed(0f),
            rowSpacing = json.optionalMap("row_spacing")?.let(GridSpacing::fromJson) ?: GridSpacing.Fixed(0f),
            partialRowPosition = json["partial_row_position"]?.let(HorizontalPosition::from)
                ?: HorizontalPosition.START,
            contentPosition = json["content_position"]?.let(Position::fromJson) ?: DEFAULT_CONTENT_POSITION,
            cellAspectRatio = json.optionalField<Double>("cell_aspect_ratio")?.takeIf { it > 0 },
            percentHeightBasis = json.optionalField<String>("percent_height_basis")
                ?.let(PercentHeightBasis::from) ?: PercentHeightBasis.ROW
        )
    }
}

/** What a grid cell's percentage height is a share of. */
internal enum class PercentHeightBasis(val value: String) {
    /** Its own row, as a percentage across a horizontal linear layout is. */
    ROW("row"),

    /**
     * The tallest of the rows holding a percentage cell, which all take that height. `100%` on
     * every item gives equal-height cards; rows of only `auto` and fixed cells keep their own.
     */
    TALLEST_ROW("tallest_row");

    companion object {
        @Throws(JsonException::class)
        fun from(value: String): PercentHeightBasis = entries.firstOrNull { it.value == value }
            ?: throw JsonException("Unknown PercentHeightBasis: $value")
    }
}

/**
 * How many columns a grid has: always a count, or as many as fit its width.
 *
 * [Fixed] suits layouts built from spans, since a span keeps its share of the row: a span of 6 is
 * half of a 12-column grid at every width. [Fit] suits rows of like options, whose count should
 * follow the width: 44pt columns 8 apart fit floor(328 / 52) = 6 across a 320pt grid.
 */
internal sealed class GridColumns {

    /** A column count that never changes. Below 1 counts as 1. */
    data class Fixed(val count: Int) : GridColumns()

    /**
     * As many columns of at least [minWidth] as fit the grid's width, from 1 up to [max].
     *
     * Spans still count columns, not shares of the row, so a span tuned for the widest count takes
     * a bigger share as columns drop out.
     */
    data class Fit(
        val max: Int,
        val minWidth: GridColumnWidth,
        /**
         * The widest a column grows. Past it the columns stop at this width and the grid's
         * `content_position` places them; equal to [minWidth], every column is that wide at any
         * width. Below [minWidth] it counts as [minWidth].
         */
        val maxWidth: GridColumnWidth? = null
    ) : GridColumns()

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridColumns =
            when (val type = json.requireField<String>("type")) {
                "fixed" -> Fixed(count = json.requireField<Double>("count").toInt())
                "fit" -> Fit(
                    max = json.requireField<Double>("max").toInt(),
                    minWidth = GridColumnWidth.fromJson(json.requireField("min_width")),
                    maxWidth = json.optionalMap("max_width")?.let(GridColumnWidth::fromJson)
                )
                else -> throw JsonException("Unknown GridColumns type: $type")
            }
    }
}

/** How one grid item is placed among its grid's columns. */
internal data class GridItemConfig(
    /** The columns the cell asks to cover. Placement caps it between 1 and the column count. */
    val columnSpan: Int = 1,
    /**
     * Where the item falls among its siblings: items place, and are read, in ascending order, in
     * list order among equal ones. After a `randomize_children` shuffle, so the shuffle mixes only
     * items of one order.
     */
    val order: Double = 0.0,
    /**
     * The column the item starts at, from 0 at the start edge, instead of the next free one; null
     * for the next free one. See [com.urbanairship.android.layout.util.placeGridItems].
     */
    val columnStart: Int? = null
) {

    companion object {
        fun fromJson(json: JsonMap): GridItemConfig = GridItemConfig(
            columnSpan = (json.optionalField<Double>("column_span") ?: 1.0).toInt(),
            order = json.optionalField<Double>("order") ?: 0.0,
            columnStart = json.optionalField<Double>("column_start")?.toInt()
        )
    }
}

/**
 * The space between a grid's columns or rows: always a set space, or at least a minimum and wider
 * to use room the columns or rows leave over, up to a maximum.
 *
 * Room goes to the columns first, up to `max_width`, and only what they leave to [Spread] gaps, so
 * full rows reach both edges. Rows keep their heights and spread into the height a grid has of its
 * own. Where nothing is left over, [Spread] is its minimum, and past its maximum
 * `content_position` places the cells. The column count always uses the minimum.
 */
internal sealed class GridSpacing {

    /** The least this spacing can be, never below 0. */
    abstract val min: Float

    /** A space that never changes. */
    data class Fixed(val space: Float) : GridSpacing() {
        override val min: Float get() = space.coerceAtLeast(0f)
    }

    /** At least [minSpace], widened to use leftover room up to [maxSpace]. */
    data class Spread(val minSpace: Float, val maxSpace: Float? = null) : GridSpacing() {
        override val min: Float get() = minSpace.coerceAtLeast(0f)
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridSpacing =
            when (val type = json.requireField<String>("type")) {
                "fixed" -> Fixed(space = json.requireField("space"))
                "spread" -> Spread(minSpace = json.requireField("min_space"), maxSpace = json.optionalField("max_space"))
                else -> throw JsonException("Unknown GridSpacing type: $type")
            }
    }
}

/**
 * A bound on a `fit` grid's column width, for its `min_width` and `max_width`.
 *
 * A [Type.TEXT_SCALED] bound grows with the accessibility text size, as labels do: 44 at 200% text
 * is 88.
 */
internal data class GridColumnWidth(val type: Type, val size: Float) {

    enum class Type(val value: String) {
        FIXED("fixed"),
        TEXT_SCALED("text_scaled")
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridColumnWidth {
            val type = json.requireField<String>("type")
            return GridColumnWidth(
                type = Type.entries.firstOrNull { it.value == type }
                    ?: throw JsonException("Unknown GridColumnWidth type: $type"),
                size = json.requireField("size")
            )
        }
    }
}
