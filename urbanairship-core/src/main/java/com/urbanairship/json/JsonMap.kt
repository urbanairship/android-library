/* Copyright Airship and Contributors */
package com.urbanairship.json

import com.urbanairship.UALog
import org.json.JSONException
import org.json.JSONStringer

/**
 * An immutable mapping of String keys to JsonValues.
 */
public class JsonMap public constructor(
    map: Map<String, JsonValue>?
) : Iterable<Map.Entry<String, JsonValue>>, JsonSerializable {

    private val mutableMap: MutableMap<String, JsonValue> = map?.toMutableMap() ?: mutableMapOf()

    /**
     * Gets the [JsonMap] as a [Map].
     */
    public val map: Map<String, JsonValue>
        get() = mutableMap.toMap()

    /**
     * Returns whether this map contains the specified key.
     *
     * @param key the key to search for.
     * @return `true` if this map contains the specified key,
     * `false` otherwise.
     */
    public fun containsKey(key: String): Boolean {
        return mutableMap.containsKey(key)
    }

    /**
     * Returns whether this map contains the specified value.
     *
     * @param value the value to search for.
     * @return `true` if this map contains the specified value,
     * `false` otherwise.
     */
    public fun containsValue(value: JsonValue): Boolean {
        return mutableMap.containsValue(value)
    }

    /**
     * Returns a set containing all of the mappings in this map. Each mapping is
     * an instance of [Map.Entry]. As the set is backed by this map,
     * changes in one will be reflected in the other.
     *
     * @return a set of the mappings.
     */
    public fun entrySet(): Set<Map.Entry<String, JsonValue>> {
        return mutableMap.entries
    }

    /**
     * Returns the value of the mapping with the specified key.
     *
     * @param key the key.
     * @return the value of the mapping with the specified key, or `null`
     * if no mapping for the specified key is found.
     */
    public operator fun get(key: String): JsonValue? {
        return mutableMap[key]
    }

    /**
     * Returns the optional value in the map with the specified key. If the value is not in the map
     * [JsonValue.NULL] will be returned instead `null`.
     *
     * @param key the key.
     * @return the value of the mapping with the specified key, or [JsonValue.NULL]
     * if no mapping for the specified key is found.
     */
    public fun opt(key: String): JsonValue {
        return get(key) ?: JsonValue.NULL
    }

    /**
     * Returns the required value in the map with the specified key. If the value is not in the map
     * an exception will be thrown.
     *
     * @param key the key.
     * @return The value of the mapping with the specified key.
     * @throws JsonException if the value is not in the map.
     */
    @Throws(JsonException::class)
    public fun require(key: String): JsonValue {
        val value = get(key) ?: throw JsonException("Expected value for key: $key")
        return value
    }

    public val isEmpty: Boolean
        /**
         * Returns whether this map is empty.
         *
         * @return `true` if this map has no elements, `false`
         * otherwise.
         */
        get() = mutableMap.isEmpty()

    public val isNotEmpty: Boolean
        /**
         * Returns whether this map is not empty.
         *
         * @return `true` if this map has elements, `false`
         * otherwise.
         */
        get() = !isEmpty

    /**
     * Returns a set of the keys contained in this map. The set is backed by
     * this map so changes to one are reflected by the other. The set does not
     * support adding.
     *
     * @return a set of the keys.
     */
    public fun keySet(): Set<String> {
        return mutableMap.keys
    }

    /**
     * Returns the number of elements in this map.
     *
     * @return the number of elements in this map.
     */
    public fun size(): Int {
        return mutableMap.size
    }

    /**
     * Returns a collection of the values contained in this map.
     *
     * @return a collection of the values contained in this map.
     */
    public fun values(): Collection<JsonValue> {
        return mutableMap.values.toList()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other is JsonMap) {
            return mutableMap == other.mutableMap
        }

        if (other is JsonValue) {
            return mutableMap == other.optMap().mutableMap
        }

        return false
    }

    override fun hashCode(): Int {
        return mutableMap.hashCode()
    }

    /**
     * Returns the JsonMap as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    override fun toString(): String {
        return toString(false)
    }

    /**
     * Returns the JsonMap as a JSON encoded String with sorted keys.
     *
     * @return The value as a JSON encoded String.
     */
    public fun toString(sortKeys: Boolean): String {
        try {
            val stringer = JSONStringer()
            write(stringer, sortKeys)
            return stringer.toString()
        } catch (e: JSONException) {
            // Should never happen
            UALog.e(e, "JsonMap - Failed to create JSON String.")
            return ""
        } catch (e: StringIndexOutOfBoundsException) {
            UALog.e(e, "JsonMap - Failed to create JSON String.")
            return ""
        }
    }

    /**
     * Helper method that is used to write the value as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws org.json.JSONException If the value is unable to be written as JSON.
     */
    @Throws(JSONException::class)
    public fun write(stringer: JSONStringer, sortKeys: Boolean) {
        stringer.`object`()

        val entries = if (sortKeys) {
            entrySet().sortedBy { it.key }
        } else {
            entrySet()
        }

        entries.forEach { (key, value) ->
            stringer.key(key)
            value.write(stringer, sortKeys)
        }

        stringer.endObject()
    }

    override fun iterator(): Iterator<Map.Entry<String, JsonValue>> {
        return entrySet().iterator()
    }

    override fun toJsonValue(): JsonValue {
        return JsonValue.wrap(this)
    }

    /**
     * Builder class for [com.urbanairship.json.JsonMap] Objects.
     */
    public class Builder {

        private val mutableMap: MutableMap<String, JsonValue> = mutableMapOf()

        /**
         * Add a pre-existing JSON map to the JSON map.
         *
         * @param map A JsonMap instance.
         * @return The JSON map builder.
         */
        public fun putAll(map: JsonMap): Builder {
            return this.also { builder ->
                map.entrySet().forEach { (key, value) ->
                    builder.put(key, value)
                }
            }
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a JsonSerializable.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: JsonSerializable?): Builder {
            if (value == null) {
                mutableMap.remove(key)
                return this
            }

            val jsonValue = value.toJsonValue()
            if (jsonValue.isNull) {
                mutableMap.remove(key)
            } else {
                mutableMap[key] = jsonValue
            }

            return this
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as an Object. If an exception is thrown while attempting to wrap
         * this object as a JsonValue, it will be swallowed and the entry will be dropped from the map.
         * @return The JSON map builder.
         */
        public fun putOpt(key: String, value: Any?): Builder {
            put(key, JsonValue.wrapOpt(value))
            return this
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a String.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: String?): Builder {
            if (value != null) {
                put(key, JsonValue.wrap(value))
            } else {
                mutableMap.remove(key)
            }

            return this
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a boolean.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: Boolean): Builder {
            return put(key, JsonValue.wrap(value))
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as an int.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: Int): Builder {
            return put(key, JsonValue.wrap(value))
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a long.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: Long): Builder {
            return put(key, JsonValue.wrap(value))
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a double.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: Double): Builder {
            return put(key, JsonValue.wrap(value))
        }

        /**
         * Add a key and value to the JSON map.
         *
         * @param key The key as a String.
         * @param value The value as a char.
         * @return The JSON map builder.
         */
        public fun put(key: String, value: Char): Builder {
            return put(key, JsonValue.wrap(value))
        }

        /**
         * Create the JSON map.
         *
         * @return The created JSON map.
         */
        public fun build(): JsonMap {
            return JsonMap(mutableMap)
        }
    }

    public companion object {

        @JvmField
        public val EMPTY_MAP: JsonMap = JsonMap(null)

        /**
         * Factory method to create a new JSON map builder.
         *
         * @return A JSON map builder.
         */
        @JvmStatic
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
