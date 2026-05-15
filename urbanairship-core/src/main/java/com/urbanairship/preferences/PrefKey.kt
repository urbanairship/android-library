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
    public val name: String

    public fun serialize(value: T): String

    /**
     * Decodes a previously stored value, or returns `null` if the stored representation is
     * corrupt or no longer parseable.
     */
    public fun deserialize(stored: String): T?
}

/**
 * Preference key whose value is loaded eagerly at takeoff and accessed synchronously.
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

        public fun string(name: String): SyncPrefKey<String> =
            SyncPrefKey(name, { it }, { it })

        public fun boolean(name: String): SyncPrefKey<Boolean> =
            SyncPrefKey(name, Boolean::toString, String::toBooleanStrictOrNull)

        public fun int(name: String): SyncPrefKey<Int> =
            SyncPrefKey(name, Int::toString, String::toIntOrNull)

        public fun long(name: String): SyncPrefKey<Long> =
            SyncPrefKey(name, Long::toString, String::toLongOrNull)

        public fun json(name: String): SyncPrefKey<JsonValue> =
            SyncPrefKey(name, JsonValue::toString, ::parseJsonOrNull)

        public fun <T : JsonSerializable> jsonSerializable(
            name: String,
            fromJson: (JsonValue) -> T
        ): SyncPrefKey<T> = SyncPrefKey(
            name = name,
            toString = { it.toJsonValue().toString() },
            fromString = { stored ->
                parseJsonOrNull(stored)?.let { runCatching { fromJson(it) }.getOrNull() }
            }
        )

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

        public fun string(name: String): AsyncPrefKey<String> =
            AsyncPrefKey(name, { it }, { it })

        public fun boolean(name: String): AsyncPrefKey<Boolean> =
            AsyncPrefKey(name, Boolean::toString, String::toBooleanStrictOrNull)

        public fun int(name: String): AsyncPrefKey<Int> =
            AsyncPrefKey(name, Int::toString, String::toIntOrNull)

        public fun long(name: String): AsyncPrefKey<Long> =
            AsyncPrefKey(name, Long::toString, String::toLongOrNull)

        public fun json(name: String): AsyncPrefKey<JsonValue> =
            AsyncPrefKey(name, JsonValue::toString, ::parseJsonOrNull)

        public fun <T : JsonSerializable> jsonSerializable(
            name: String,
            fromJson: (JsonValue) -> T
        ): AsyncPrefKey<T> = AsyncPrefKey(
            name = name,
            toString = { it.toJsonValue().toString() },
            fromString = { stored ->
                parseJsonOrNull(stored)?.let { runCatching { fromJson(it) }.getOrNull() }
            }
        )

        public fun <T> custom(
            name: String,
            serialize: (T) -> String,
            deserialize: (String) -> T?
        ): AsyncPrefKey<T> = AsyncPrefKey(name, serialize, deserialize)
    }
}

private fun parseJsonOrNull(stored: String): JsonValue? =
    runCatching { JsonValue.parseString(stored) }.getOrNull()
