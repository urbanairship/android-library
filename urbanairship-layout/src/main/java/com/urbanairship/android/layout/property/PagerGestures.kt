package com.urbanairship.android.layout.property

import com.urbanairship.android.layout.info.Identifiable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField

internal sealed class PagerGesture : Identifiable {
    abstract val reportingMetadata: JsonValue?

    data class Tap(
        override val identifier: String,
        override val reportingMetadata: JsonValue?,
        val location: GestureLocation,
        val behavior: PagerGestureBehavior
    ) : PagerGesture() {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): Tap = Tap(
                identifier = json.requireField("identifier"),
                reportingMetadata = json.optionalField<JsonValue>("reporting_metadata"),
                location = GestureLocation.from(json.requireField("location")),
                behavior = PagerGestureBehavior.from(json.requireField("behavior"))
            )
        }
    }
    data class Swipe(
        override val identifier: String,
        override val reportingMetadata: JsonValue?,
        val direction: GestureDirection,
        val behavior: PagerGestureBehavior
    ) : PagerGesture() {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): Swipe = Swipe(
                identifier = json.requireField("identifier"),
                reportingMetadata = json.optionalField<JsonValue>("reporting_metadata"),
                direction = GestureDirection.from(json.requireField("direction")),
                behavior = PagerGestureBehavior.from(json.requireField("behavior"))
            )
        }
    }
    data class Hold(
        override val identifier: String,
        override val reportingMetadata: JsonValue?,
        val pressBehavior: PagerGestureBehavior,
        val releaseBehavior: PagerGestureBehavior
    ) : PagerGesture() {
        companion object {
            @Throws(JsonException::class)
            fun from(json: JsonMap): Hold = Hold(
                identifier = json.requireField("identifier"),
                reportingMetadata = json.optionalField<JsonValue>("reporting_metadata"),
                pressBehavior = PagerGestureBehavior.from(json.requireField("press_behavior")),
                releaseBehavior = PagerGestureBehavior.from(json.requireField("release_behavior"))
            )
        }
    }

    companion object {
        @Throws(JsonException::class)
        fun from(json: JsonMap): PagerGesture {
            return when (GestureType.from(json.requireField("type"))) {
                GestureType.TAP -> Tap.from(json)
                GestureType.SWIPE -> Swipe.from(json)
                GestureType.HOLD -> Hold.from(json)
            }
        }

        @Throws(JsonException::class)
        fun fromList(json: JsonList): List<PagerGesture> {
            return if (json.isEmpty) emptyList() else json.map { from(it.optMap()) }
        }
    }
}

internal data class PagerGestureBehavior(
    val actions: Map<String, JsonValue>?,
    val behaviors: List<ButtonClickBehaviorType>?
) {
    companion object {
        fun from(json: JsonMap): PagerGestureBehavior = PagerGestureBehavior(
            actions = json.optionalField<JsonMap>("actions")?.map,
            behaviors = json.optionalField<JsonList>("behaviors")?.let {
                ButtonClickBehaviorType.fromList(it)
            }
        )
    }
}

internal enum class GestureType(private val value: String) {
    TAP("tap"),
    SWIPE("swipe"),
    HOLD("hold");

    override fun toString(): String = value

    companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): GestureType {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown GestureType value: $value")
        }
    }
}

internal enum class GestureDirection(private val value: String) {
    UP("up"),
    DOWN("down");

    companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): GestureDirection {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown GestureDirection value: $value")
        }
    }
}

internal enum class GestureLocation(private val value: String) {
    TOP("top"),
    BOTTOM("bottom"),
    /**
     * For LTR and RTL support.
     * Clients determine "left" and "right" based on device locale.
     */
    START("start"),
    /**
     * For LTR and RTL support.
     * Clients determine "left" and "right" based on device locale.
     */
    END("end"),
    /** For LTR-only support. */
    LEFT("left"),
    /** For LTR-only support. */
    RIGHT("right"),
    /** For gestures anywhere in the view. */
    ANY("any");

    companion object {
        @Throws(IllegalArgumentException::class)
        fun from(value: String): GestureLocation {
            for (type in entries) {
                if (type.value == value.lowercase()) {
                    return type
                }
            }
            throw IllegalArgumentException("Unknown GestureLocation value: $value")
        }
    }
}
