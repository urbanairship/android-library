package com.urbanairship.android.layout.property

import androidx.annotation.Dimension
import com.urbanairship.android.layout.property.StoryIndicatorStyleType.LINEAR_PROGRESS
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

internal sealed class StoryIndicatorStyle(val type: StoryIndicatorStyleType) {
    class LinearProgress(
        json: JsonMap
    ) : StoryIndicatorStyle(LINEAR_PROGRESS) {
        val direction: Direction = Direction.from(json.require("direction"))
        val sizing: SizingType? = json.optionalField<String>("sizing")?.let {
            SizingType.from(it)
        }
        @get:Dimension(unit = Dimension.DP)
        val spacing: Int = json.optionalField<Int>("spacing") ?: 4
        val trackColor = Color.fromJson(json.requireField("track_color"))
        val progressColor = Color.fromJson(json.requireField("progress_color"))
        val inactiveSegmentScaler: Double = json.optionalField<Double>("inactive_segment_scaler") ?: 0.5

        internal enum class SizingType(private val value: String) {
            EQUAL("equal"),
            PAGE_DURATION("page_duration");

            companion object {
                @Throws(IllegalArgumentException::class)
                fun from(value: String): SizingType {
                    for (type in entries) {
                        if (type.value == value.lowercase()) {
                            return type
                        }
                    }
                    throw IllegalArgumentException("Unknown StoryIndicator sizing value: $value")
                }
            }
        }
    }

    companion object {
        fun from(json: JsonMap): StoryIndicatorStyle {
            return when (StoryIndicatorStyleType.from(json.requireField("type"))) {
                LINEAR_PROGRESS -> LinearProgress(json)
            }
        }
    }
}
