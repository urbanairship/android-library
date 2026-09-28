/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireMap

/**
 * How a grid divides its width into columns and places its rows.
 *
 * The column count is as many columns of at least [minColumnWidth] as fit, up to [maxColumns]:
 * 320 across with a 44 minimum and 8 spacing fits floor(328 / 52) = 6. Without a minimum the grid
 * always draws [maxColumns].
 */
internal data class GridLayoutConfig(
    /** At least 1. */
    val maxColumns: Int,
    val minColumnWidth: GridMinColumnWidth? = null,
    /** Points between columns. */
    val columnSpacing: Float = 0f,
    /** Points between rows. */
    val rowSpacing: Float = 0f,
    /** Where a row that fills fewer columns than the grid has sits. Its cells keep their widths. */
    val partialRowAlignment: HorizontalPosition = HorizontalPosition.START,
    /** Where the rows sit when the grid is taller than they are. */
    val rowAlignment: VerticalPosition = VerticalPosition.TOP,
    /** Width divided by height for every cell, or null for rows as tall as their tallest item. */
    val cellAspectRatio: Double? = null
) {

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridLayoutConfig = GridLayoutConfig(
            maxColumns = json.requireField<Double>("max_columns").toInt().coerceAtLeast(1),
            minColumnWidth = json.optionalMap("min_column_width")?.let(GridMinColumnWidth::fromJson),
            columnSpacing = json.optionalField<Float>("column_spacing") ?: 0f,
            rowSpacing = json.optionalField<Float>("row_spacing") ?: 0f,
            partialRowAlignment = json["partial_row_alignment"]?.let(HorizontalPosition::from)
                ?: HorizontalPosition.START,
            rowAlignment = json["row_alignment"]?.let(VerticalPosition::from) ?: VerticalPosition.TOP,
            cellAspectRatio = json.optionalField<Double>("cell_aspect_ratio")?.takeIf { it > 0 }
        )
    }
}

/**
 * The narrowest a grid column may be before the grid drops a column.
 *
 * A [Type.TEXT_SCALED] minimum grows with the accessibility text size, as labels do: 44 points at
 * 200% text is 88.
 */
internal data class GridMinColumnWidth(val type: Type, val points: Float) {

    enum class Type(val value: String) {
        FIXED("fixed"),
        TEXT_SCALED("text_scaled")
    }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridMinColumnWidth {
            val type = json.requireField<String>("type")
            return GridMinColumnWidth(
                type = Type.entries.firstOrNull { it.value == type }
                    ?: throw JsonException("Unknown GridMinColumnWidth type: $type"),
                points = json.requireField("points")
            )
        }
    }
}

/**
 * A range of lengths in points, both ends inclusive, as in a JSON predicate's `at_least` and
 * `at_most`. An omitted end is unbounded.
 */
internal data class LengthRange(val atLeast: Float? = null, val atMost: Float? = null) {

    operator fun contains(length: Float): Boolean =
        (atLeast == null || length >= atLeast) && (atMost == null || length <= atMost)

    companion object {
        fun fromJson(json: JsonMap): LengthRange = LengthRange(
            atLeast = json.optionalField("at_least"),
            atMost = json.optionalField("at_most")
        )
    }
}

/**
 * A config for the conditions it names; a condition left out matches anything. The config
 * replaces the grid's default whole rather than merging into it.
 */
internal data class GridLayoutConfigSelector(
    /** Kept as written, so a platform this SDK doesn't know never matches rather than failing the scene. */
    val platform: String? = null,
    val windowSize: WindowSize? = null,
    val orientation: Orientation? = null,
    /** Matched against the grid's own width. Never matches a grid with no width. */
    val gridWidth: LengthRange? = null,
    val config: GridLayoutConfig
) {

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridLayoutConfigSelector = GridLayoutConfigSelector(
            platform = json["platform"]?.requireString()?.lowercase(),
            windowSize = json["window_size"]?.let(WindowSize::from),
            orientation = json["orientation"]?.let(Orientation::from),
            gridWidth = json.optionalMap("grid_width")?.let(LengthRange::fromJson),
            config = GridLayoutConfig.fromJson(json.requireField<JsonMap>("config"))
        )
    }
}

/** A grid's `config`: the default, and the selectors that can replace it. */
internal data class GridLayoutConfigs(
    val default: GridLayoutConfig,
    val selectors: List<GridLayoutConfigSelector> = emptyList()
) {

    /** Every config the grid could lay out with. */
    val all: List<GridLayoutConfig>
        get() = listOf(default) + selectors.map { it.config }

    companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridLayoutConfigs = GridLayoutConfigs(
            default = GridLayoutConfig.fromJson(json.requireField<JsonMap>("default")),
            selectors = json["selectors"]?.optList()?.map {
                GridLayoutConfigSelector.fromJson(it.requireMap())
            } ?: emptyList()
        )
    }
}
