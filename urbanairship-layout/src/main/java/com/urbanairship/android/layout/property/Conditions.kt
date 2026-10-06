/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.property

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.environment.ThomasEnvironment
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/**
 * A range of lengths in dp, both ends inclusive, as a JSON predicate's `at_least` and `at_most`
 * are. An omitted end is unbounded.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class LengthRange(val atLeast: Double? = null, val atMost: Double? = null) {

    /**
     * Returns whether [length] lies in the range, ends included.
     *
     * @param length the length, or null where it isn't known, which no range admits.
     */
    internal fun admits(length: Double?): Boolean =
        length != null && (atLeast == null || length >= atLeast) && (atMost == null || length <= atMost)

    internal companion object {
        fun fromJson(json: JsonMap): LengthRange =
            LengthRange(atLeast = json.optionalField("at_least"), atMost = json.optionalField("at_most"))
    }
}

/** Whether a range a selector may leave out admits [length]: anything when it's left out. */
private fun LengthRange?.admitsIfNamed(length: Double?): Boolean = this == null || admits(length)

/**
 * One entry in an environment condition: the shared selector fields, except
 * `environment_conditions`, plus sizes, and the value it chooses.
 *
 * A length that isn't known, such as the height of a frame its content sizes, matches no range.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class EnvironmentConditionSelector(
    val shared: SelectorConditions,
    val windowWidth: LengthRange? = null,
    val windowHeight: LengthRange? = null,
    /** The window less its safe-area insets. */
    val safeWindowWidth: LengthRange? = null,
    val safeWindowHeight: LengthRange? = null,
    /** The space the host gives the scene. */
    val frameWidth: LengthRange? = null,
    val frameHeight: LengthRange? = null,
    /** Any JSON value; a string bucket such as `compact` is the usual case. */
    val value: JsonValue
) {

    /**
     * Returns whether this selector matches [environment].
     *
     * @param shared [environment]'s shared selector fields, built once for every selector checked.
     */
    internal fun matches(environment: ThomasEnvironment, shared: SelectorEnvironment): Boolean =
        this.shared.matches(shared) &&
            windowWidth.admitsIfNamed(environment.windowWidth) &&
            windowHeight.admitsIfNamed(environment.windowHeight) &&
            safeWindowWidth.admitsIfNamed(environment.safeWindowWidth) &&
            safeWindowHeight.admitsIfNamed(environment.safeWindowHeight) &&
            frameWidth.admitsIfNamed(environment.frameWidth) &&
            frameHeight.admitsIfNamed(environment.frameHeight)

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): EnvironmentConditionSelector = EnvironmentConditionSelector(
            shared = SelectorConditions.fromJson(json).copy(environmentConditions = null),
            windowWidth = json.optionalMap("window_width")?.let(LengthRange::fromJson),
            windowHeight = json.optionalMap("window_height")?.let(LengthRange::fromJson),
            safeWindowWidth = json.optionalMap("safe_window_width")?.let(LengthRange::fromJson),
            safeWindowHeight = json.optionalMap("safe_window_height")?.let(LengthRange::fromJson),
            frameWidth = json.optionalMap("frame_width")?.let(LengthRange::fromJson),
            frameHeight = json.optionalMap("frame_height")?.let(LengthRange::fromJson),
            value = json.requireField("value")
        )
    }
}

/**
 * One entry in a grid condition: a range on the grid's content width, and the value it chooses.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class GridConditionSelector(val width: LengthRange? = null, val value: JsonValue) {

    internal companion object {
        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): GridConditionSelector = GridConditionSelector(
            width = json.optionalMap("width")?.let(LengthRange::fromJson),
            value = json.requireField("value")
        )
    }
}

/**
 * Named breakpoints, each a list of selectors whose first match chooses the condition's value.
 *
 * For example, `size: [{window_width: {at_most: 599}, value: compact}, {value: expanded}]`
 * chooses `compact` in a 390-wide window and `expanded` in a 1024-wide one.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class Conditions<T> internal constructor(private val selectors: Map<String, List<T>>) {

    /**
     * Returns each condition's chosen value: its first selector that [matches]. A condition no
     * selector matches is left out.
     */
    internal fun choose(matches: (T) -> Boolean, value: (T) -> JsonValue): JsonMap {
        val builder = JsonMap.newBuilder()
        selectors.forEach { (name, list) -> list.firstOrNull(matches)?.let { builder.put(name, value(it)) } }
        return builder.build()
    }

    internal companion object {
        @Throws(JsonException::class)
        fun <T> fromJson(json: JsonMap?, parse: (JsonMap) -> T): Conditions<T> = Conditions(
            json?.map?.mapValues { (_, list) -> list.requireList().map { parse(it.requireMap()) } }.orEmpty()
        )
    }
}

/** The scene's `environment_state.conditions`. */
internal typealias EnvironmentConditions = Conditions<EnvironmentConditionSelector>

/** Chooses each environment condition's value from the measured [environment]. */
internal fun EnvironmentConditions.choose(environment: ThomasEnvironment): JsonMap {
    val shared = SelectorEnvironment.from(environment)
    return choose({ it.matches(environment, shared) }, { it.value })
}

/** A grid's `grid_state.conditions`. */
internal typealias GridConditions = Conditions<GridConditionSelector>

/**
 * Chooses each grid condition's value from the grid's content width.
 *
 * @param width the grid's inner width in dp, or null for a grid with none to measure.
 */
internal fun GridConditions.choose(width: Double?): JsonMap =
    choose({ it.width.admitsIfNamed(width) }, { it.value })

/** Reads `<key>.conditions` from [json], as `environment_state` and `grid_state` hold them. */
@Throws(JsonException::class)
internal fun <T> JsonMap.conditionsIn(key: String, parse: (JsonMap) -> T): Conditions<T> =
    Conditions.fromJson(optionalMap(key)?.optionalMap("conditions"), parse)
