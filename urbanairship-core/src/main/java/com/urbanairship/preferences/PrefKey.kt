/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import androidx.annotation.RestrictTo
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * Typed preference key.
 *
 * The key encodes both the value type [T] and the access mode (sync vs async). Callers obtain
 * values via [PreferenceStore.get] / [PreferenceStore.put], which resolve to the appropriate
 * overload based on the key's subtype: [SyncPrefKey] for regular access, [AsyncPrefKey] for
 * `suspend` access.
 *
 * Construct keys via the companion factories on [SyncPrefKey] / [AsyncPrefKey]. To support a
 * value type that isn't built in, use the `custom` factory and provide explicit serialize /
 * deserialize lambdas.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed interface PrefKey<T> {

    /** The unique string identifier under which this key's value is persisted. */
    public val name: String

    /** Encodes [value] to the string representation stored in the underlying preference table. */
    public fun serialize(value: T): String

    /**
     * Decodes a previously stored value. Returns `null` if [stored] is not a valid encoding for
     * this key's value type — for example, a non-numeric string under an `int` key, or
     * unparseable JSON under a `json` key.
     */
    public fun deserialize(stored: String): T?
}

/**
 * Preference key whose value is loaded eagerly at takeoff and accessed synchronously.
 *
 * Use the companion factories to construct: [string], [boolean], [int], [long], [json],
 * [jsonSerializable], or [custom] for arbitrary types.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SyncPrefKey<T> private constructor(
    public override val name: String,
    private val toString: (T) -> String,
    private val fromString: (String) -> T?
) : PrefKey<T> {

    public override fun serialize(value: T): String = toString(value)
    public override fun deserialize(stored: String): T? = fromString(stored)

    public companion object {

        /** Key for a non-null [String] value. Stored verbatim. */
        public fun string(name: String): SyncPrefKey<String> =
            SyncPrefKey(name, { it }, { it })

        /**
         * Key for a [Boolean] value. Stored as `"true"` / `"false"`. Stored values that aren't
         * strictly one of those two strings deserialize to `null`.
         */
        public fun boolean(name: String): SyncPrefKey<Boolean> =
            SyncPrefKey(name, Boolean::toString, String::toBooleanStrictOrNull)

        /** Key for an [Int] value. Stored as decimal text; non-integer stored values deserialize to `null`. */
        public fun int(name: String): SyncPrefKey<Int> =
            SyncPrefKey(name, Int::toString, String::toIntOrNull)

        /** Key for a [Long] value. Stored as decimal text; non-long stored values deserialize to `null`. */
        public fun long(name: String): SyncPrefKey<Long> =
            SyncPrefKey(name, Long::toString, String::toLongOrNull)

        /** Key for a [JsonValue]. Unparseable stored values deserialize to `null` (logged by [PreferenceStore]). */
        public fun json(name: String): SyncPrefKey<JsonValue> =
            SyncPrefKey(name, JsonValue::toString, JsonValue::parseString)

        /**
         * Key for any [JsonSerializable] type. Values are written as the JSON string form of
         * [JsonSerializable.toJsonValue]; reads parse the stored JSON and invoke [fromJson]. If
         * the stored JSON is unparseable or [fromJson] throws, the read returns `null` and the
         * error is logged by [PreferenceStore]. The corrupt row is left in place — if a specific
         * key needs cleanup, handle it inline at the call site.
         */
        public fun <T : JsonSerializable> jsonSerializable(
            name: String,
            fromJson: (JsonValue) -> T
        ): SyncPrefKey<T> = SyncPrefKey(
            name = name,
            toString = { it.toJsonValue().toString() },
            fromString = { fromJson(JsonValue.parseString(it)) }
        )

        /**
         * Escape hatch for value types not covered by the built-in factories.
         *
         * Serialization or deserialization errors thrown by these lambdas are handled by
         * [PreferenceStore]: a throwing [serialize] logs and drops the write; a throwing
         * [deserialize] logs and returns `null` (key treated as unset).
         */
        public fun <T> custom(
            name: String,
            serialize: (T) -> String,
            deserialize: (String) -> T?
        ): SyncPrefKey<T> = SyncPrefKey(name, serialize, deserialize)
    }
}

/**
 * Preference key whose value is loaded lazily and accessed from a coroutine.
 *
 * Identical surface to [SyncPrefKey], but [PreferenceStore.get] / [PreferenceStore.put] for these
 * keys are `suspend` — reading or writing an async key outside a coroutine context is a compile
 * error.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AsyncPrefKey<T> private constructor(
    public override val name: String,
    private val toString: (T) -> String,
    private val fromString: (String) -> T?
) : PrefKey<T> {

    public override fun serialize(value: T): String = toString(value)
    public override fun deserialize(stored: String): T? = fromString(stored)

    public companion object {

        /** Key for a non-null [String] value. Stored verbatim. */
        public fun string(name: String): AsyncPrefKey<String> =
            AsyncPrefKey(name, { it }, { it })

        /**
         * Key for a [Boolean] value. Stored as `"true"` / `"false"`. Stored values that aren't
         * strictly one of those two strings deserialize to `null`.
         */
        public fun boolean(name: String): AsyncPrefKey<Boolean> =
            AsyncPrefKey(name, Boolean::toString, String::toBooleanStrictOrNull)

        /** Key for an [Int] value. Stored as decimal text; non-integer stored values deserialize to `null`. */
        public fun int(name: String): AsyncPrefKey<Int> =
            AsyncPrefKey(name, Int::toString, String::toIntOrNull)

        /** Key for a [Long] value. Stored as decimal text; non-long stored values deserialize to `null`. */
        public fun long(name: String): AsyncPrefKey<Long> =
            AsyncPrefKey(name, Long::toString, String::toLongOrNull)

        /** Key for a [JsonValue]. Unparseable stored values deserialize to `null` (logged by [PreferenceStore]). */
        public fun json(name: String): AsyncPrefKey<JsonValue> =
            AsyncPrefKey(name, JsonValue::toString, JsonValue::parseString)

        /**
         * Key for any [JsonSerializable] type. Values are written as the JSON string form of
         * [JsonSerializable.toJsonValue]; reads parse the stored JSON and invoke [fromJson]. If
         * the stored JSON is unparseable or [fromJson] throws, the read returns `null` and the
         * error is logged by [PreferenceStore]. The corrupt row is left in place — if a specific
         * key needs cleanup, handle it inline at the call site.
         */
        public fun <T : JsonSerializable> jsonSerializable(
            name: String,
            fromJson: (JsonValue) -> T
        ): AsyncPrefKey<T> = AsyncPrefKey(
            name = name,
            toString = { it.toJsonValue().toString() },
            fromString = { fromJson(JsonValue.parseString(it)) }
        )

        /**
         * Escape hatch for value types not covered by the built-in factories.
         *
         * Serialization or deserialization errors thrown by these lambdas are handled by
         * [PreferenceStore]: a throwing [serialize] logs and drops the write; a throwing
         * [deserialize] logs and returns `null` (key treated as unset).
         */
        public fun <T> custom(
            name: String,
            serialize: (T) -> String,
            deserialize: (String) -> T?
        ): AsyncPrefKey<T> = AsyncPrefKey(name, serialize, deserialize)
    }
}
