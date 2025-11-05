/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.Dimension
import com.urbanairship.android.layout.shape.Shape
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

/** Types of Score views, used in `ScoreStyle`.  */
internal enum class ScoreType(private val value: String) {

    NUMBER_RANGE("number_range");

    override fun toString(): String {
        return name.lowercase()
    }

    companion object {

        @Throws(JsonException::class)
        fun from(value: String): ScoreType {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw JsonException("Unknown ScoreType value: $value")
        }
    }
}

/** Styling info for score views.  */
internal sealed class ScoreStyle(val type: ScoreType) {

    data class NumberRange(
        val start: Int,
        val end: Int,
        @get:Dimension(unit = Dimension.DP)
        val spacing: Int,
        val bindings: Bindings,
    ) : ScoreStyle(ScoreType.NUMBER_RANGE) {

        companion object {

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): ScoreStyle {
                val start = json.opt("start").getInt(0)
                val end = json.opt("end").getInt(10)
                val spacing = json.opt("spacing").getInt(0)
                val bindingsJson = json.opt("bindings").optMap()
                val bindings = Bindings.fromJson(bindingsJson)

                return NumberRange(start, end, spacing, bindings)
            }
        }
    }

    /** Wrapper for the `NumberRange` class with a `wrapping` field.  */
    data class WrappingNumberRange(
        val start: Int,
        val end: Int,
        @get:Dimension(unit = Dimension.DP)
        val spacing: Int,
        val bindings: Bindings,
        val wrapping: Wrapping
    ) : ScoreStyle(ScoreType.NUMBER_RANGE) {

        data class Wrapping(
            val maxItemsPerLine: Int,
            @get:Dimension(unit = Dimension.DP)
            val lineSpacing: Int
        ) {
            companion object {

                fun fromJson(json: JsonMap): Wrapping {
                    val maxItemsPerLine = json.opt("max_items_per_line").getInt(1)
                    val lineSpacing = json.opt("line_spacing").getInt(0)
                    return Wrapping(maxItemsPerLine, lineSpacing)
                }
            }
        }

        companion object {

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): ScoreStyle {
                val start = json.opt("start").getInt(0)
                val end = json.opt("end").getInt(10)
                val spacing = json.opt("spacing").getInt(0)
                val bindingsJson = json.opt("bindings").optMap()
                val bindings = Bindings.fromJson(bindingsJson)
                val wrapping: Wrapping = json.requireField<JsonMap>("wrapping").let {
                    Wrapping.fromJson(it)
                }

                return WrappingNumberRange(start, end, spacing, bindings, wrapping)
            }
        }
    }

    data class Bindings(
        val selected: Binding,
        val unselected: Binding
    ) {
        companion object {

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Bindings {
                val selectedJson = json.opt("selected").optMap()
                val unselectedJson = json.opt("unselected").optMap()
                val selected = Binding.fromJson(selectedJson)
                val unselected = Binding.fromJson(unselectedJson)

                return Bindings(selected, unselected)
            }
        }
    }

    data class Binding(
        val shapes: List<Shape>,
        val textAppearance: TextAppearance
    ) {
        companion object {

            @Throws(JsonException::class)
            fun fromJson(json: JsonMap): Binding {
                return Binding(
                    shapes = json.opt("shapes").optList().map(Shape::fromJson),
                    textAppearance = TextAppearance.fromJson(json.require("text_appearance"))
                )
            }
        }
    }

    companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ScoreStyle {
            val type = json.requireField<String>("type").let { ScoreType.from(it) }

            return when (type) {
                ScoreType.NUMBER_RANGE -> {
                    val wrapping = json.optionalField<JsonMap>("wrapping")?.let {
                        WrappingNumberRange.Wrapping.fromJson(it)
                    }

                    if (wrapping != null) {
                        WrappingNumberRange.fromJson(json)
                    } else {
                        NumberRange.fromJson(json)
                    }
                }
            }
        }
    }
}
