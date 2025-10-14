/* Copyright Airship and Contributors */
package com.urbanairship.json

import android.os.Parcel
import android.os.Parcelable
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonValue.Companion.parseString
import com.urbanairship.json.JsonValue.Companion.wrap
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONStringer
import org.json.JSONTokener

/**
 * A JsonValue is a representation of any value that can be described using JSON. It can contain one
 * of the following: a [JsonMap], a [JsonList], a [Number], a [Boolean], [String], or it can contain null.
 *
 *
 * JsonValues can be created from Java Objects by calling [wrap] or from a JSON
 * String by calling [parseString]. The JsonValue [toString] returns the
 * JSON String representation of the object.
 */
public class JsonValue private constructor(
    /**
     * Gets the raw value of the JsonValue. Will be either a [String], [Boolean], [Long], [Double], [Integer],
     * [JsonMap], [JsonList], or null.
     */
    @JvmField public val value: Any?
) : Parcelable, JsonSerializable {

    public val string: String?
        /**
         * Gets the contained value as a String.
         */
        get() {
            if (this.isString) {
                return value as String
            }

            return null
        }

    /**
     * Requires the value to be a String or a [JsonException] will be thrown.
     * @return The value as a string.
     * @throws JsonException
     */
    @Throws(JsonException::class)
    public fun requireString(): String {
        val value = string ?: throw JsonException("Expected string: $this")
        return value
    }

    /**
     * Requires the value to be a [JsonMap] or a [JsonException] will be thrown.
     * @return The value as a map.
     * @throws JsonException
     */
    @Throws(JsonException::class)
    public fun requireMap(): JsonMap {
        val value = map ?: throw JsonException("Expected map: $this")
        return value
    }

    /**
     * Requires the value to be a [JsonList] or a [JsonException] will be thrown.
     * @return The value as a list.
     * @throws JsonException
     */
    @Throws(JsonException::class)
    public fun requireList(): JsonList {
        val value = list ?: throw JsonException("Expected list: $this")
        return value
    }

    /**
     * Gets the contained values as a String.
     *
     * @param defaultValue The default value if the contained value is not a String.
     * @return The value as a String, or the defaultValue if the value is not a String.
     */
    public fun getString(defaultValue: String): String {
        return string ?: defaultValue
    }

    /**
     * Returns the String value or an empty String.
     *
     * @return The string value or an empty String.
     */
    public fun optString(): String {
        return getString("")
    }

    /**
     * Gets the contained value coerced into a String.
     *
     * @return The value coerced as a String, or null if the value is not coercible into a String.
     */
    public fun coerceString(): String? {
        if (value == null || value === NULL) {
            return null
        }

        if (value is JsonMap || value is JsonList) {
            return null
        }

        if (this.isString) {
            return value as String
        }

        if (this.isNumber) {
            try {
                return JSONObject.numberToString(value as Number)
            } catch (e: JSONException) {
                // Should never happen
                UALog.e(e, "JsonValue - Failed to coerce JSON Number into String.")
                return null
            }
        }

        return value.toString()
    }

    /**
     * Gets the contained values as an int.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as an int, or the defaultValue if the value is not a number.
     */
    public fun getInt(defaultValue: Int): Int {
        if (value == null) {
            return defaultValue
        }

        if (this.isInteger) {
            return value as Int
        }

        if (this.isNumber) {
            return (value as Number).toInt()
        }

        return defaultValue
    }

    public val integer: Int?
        /**
         * Gets the contained values as an Integer.
         */
        get() {
            if (this.isInteger) {
                return value as Int?
            }

            if (this.isNumber) {
                return (value as Number).toInt()
            }

            return null
        }

    /**
     * Gets the contained values as a float.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a float, or the defaultValue if the value is not a number.
     */
    public fun getFloat(defaultValue: Float): Float {
        if (value == null) {
            return defaultValue
        }

        if (this.isFloat) {
            return value as Float
        }

        if (this.isNumber) {
            return (value as Number).toFloat()
        }

        return defaultValue
    }

    /**
     * Gets the contained values as an double.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a double, or the defaultValue if the value is not a number.
     */
    public fun getDouble(defaultValue: Double): Double {
        if (value == null) {
            return defaultValue
        }

        if (this.isDouble) {
            return value as Double
        }

        if (this.isNumber) {
            return (value as Number).toDouble()
        }

        return defaultValue
    }

    /**
     * Gets the contained values as an long.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a long, or the defaultValue if the value is not a number.
     */
    public fun getLong(defaultValue: Long): Long {
        if (value == null) {
            return defaultValue
        }

        if (this.isLong) {
            return value as Long
        }

        if (this.isNumber) {
            return (value as Number).toLong()
        }

        return defaultValue
    }

    public val number: Number?
        /**
         * Gets the contained values as a Number.
         */
        get() {
            if (value == null) {
                return null
            }

            if (this.isNumber) {
                return value as Number
            }

            return null
        }

    /**
     * Gets the contained values as a boolean.
     *
     * @param defaultValue The default value if the contained value is not a boolean.
     * @return The value as a boolean, or the defaultValue if the value is not a boolean.
     */
    public fun getBoolean(defaultValue: Boolean): Boolean {
        if (value == null) {
            return defaultValue
        }

        if (this.isBoolean) {
            return value as Boolean
        }

        return defaultValue
    }

    public val boolean: Boolean?
        /**
         * Gets the contained values as a boolean.
         */
        get() {
            if (value == null) {
                return null
            }

            if (this.isBoolean) {
                return value as Boolean
            }

            return null
        }

    public val list: JsonList?
        /**
         * Gets the contained value as a JsonList.
         */
        get() {
            if (value == null) {
                return null
            }

            if (this.isJsonList) {
                return value as JsonList
            }

            return null
        }

    /**
     * Gets the contained values as a JsonList.
     *
     * @return The value as JsonList, or an empty JsonList if the value is not a JsonList.
     */
    public fun optList(): JsonList {
        val value = list
        return value ?: JsonList.EMPTY_LIST
    }

    public val map: JsonMap?
        /**
         * Gets the contained values as a JsonMap.
         *
         * @return The value as JsonMap, or null if the value is not a JsonMap.
         */
        get() {
            if (value == null) {
                return null
            }

            if (this.isJsonMap) {
                return value as JsonMap
            }

            return null
        }

    /**
     * Gets the contained values as a JsonMap.
     *
     * @return The value as JsonMap, or an empty JsonMap if the value is not a JsonMap.
     */
    public fun optMap(): JsonMap {
        return map ?: JsonMap.EMPTY_MAP
    }

    /**
     * If the contained value is null.
     */
    public val isNull: Boolean
        get() = value == null

    /**
     * Checks if the value is a String.
     */
    public val isString: Boolean
        get() = value is String

    /**
     * Checks if the value is an Integer.
     */
    public val isInteger: Boolean
        get() = value is Int

    /**
     * Checks if the value is a Double.
     */
    public val isDouble: Boolean
        get() = value is Double

    /**
     * Checks if the value is a Float.
     */
    public val isFloat: Boolean
        get() = value is Float

    /**
     * Checks if the value is a Long.
     */
    public val isLong: Boolean
        get() = value is Long

    /**
     * Checks if the value is a Number.
     */
    public val isNumber: Boolean
        get() = value is Number

    /**
     * Checks if the value is a Boolean.
     */
    public val isBoolean: Boolean
        get() = value is Boolean


    /**
     * Checks if the value is a JsonMap.
     */
    public val isJsonMap: Boolean
        get() = value is JsonMap

    /**
     * Checks if the value is a JsonList.
     */
    public val isJsonList: Boolean
        get() = value is JsonList

    override fun equals(other: Any?): Boolean {
        if (other !is JsonValue) {
            return false
        }

        if (value == null) {
            return other.isNull
        }

        if (isNumber && other.isNumber) {
            if (isDouble || other.isDouble) {
                return getDouble(0.0) == other.getDouble(0.0)
            }

            if (isFloat || other.isFloat) {
                return getFloat(0f) == other.getFloat(0f)
            }

            return getLong(0) == other.getLong(0)
        }

        return value == other.value
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(value)
    }

    /**
     * Returns the JsonValue as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    override fun toString(): String {
        return toString(false)
    }

    /**
     * Returns the JsonValue as a JSON encoded String with sorted keys.
     *
     * @return The value as a JSON encoded String.
     */
    public fun toString(sortKeys: Boolean): String {
        if (isNull) {
            return "null"
        }

        try {
            if (value is String) {
                return JSONObject.quote(value)
            }

            if (value is Number) {
                return JSONObject.numberToString(value)
            }

            if (value is JsonMap) {
                return value.toString(sortKeys)
            }

            if (value is JsonList) {
                return value.toString()
            }

            return value.toString()
        } catch (e: JSONException) {
            // Should never happen
            UALog.e(e, "JsonValue - Failed to create JSON String.")
            return ""
        }
    }

    /**
     * Helper method that is used to write the value as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws JSONException If the value is unable to be written as JSON.
     */
    @Throws(JSONException::class)
    public fun write(stringer: JSONStringer, sortKeys: Boolean) {
        if (isNull) {
            stringer.value(null)
            return
        }

        when (value) {
            is JsonList -> value.write(stringer, sortKeys)
            is JsonMap -> value.write(stringer, sortKeys)
            else -> stringer.value(value)
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(this.toString())
    }

    override fun toJsonValue(): JsonValue {
        return this
    }

    public companion object {

        /**
         * A null representation of the JsonValue.
         */
        @JvmField
        public val NULL: JsonValue = JsonValue(null)

        /**
         * Parse a JSON encoded String.
         *
         * @param jsonString The json encoded String.
         * @return A JsonValue from the encoded String.
         * @throws JsonException If the JSON was unable to be parsed.
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun parseString(jsonString: String?): JsonValue {
            if (jsonString.isNullOrEmpty()) {
                return NULL
            }

            val tokener = JSONTokener(jsonString)

            try {
                return wrap(tokener.nextValue())
            } catch (e: JSONException) {
                throw JsonException("Unable to parse string", e)
            }
        }

        /**
         * Wraps a String as a [JsonValue].
         *
         * @param value The value as a string.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: String?): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps a char as a [JsonValue].
         *
         * @param value The value as a char.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: Char): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps an int as a [JsonValue].
         *
         * @param value The value as an int.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: Int): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps a long as a [JsonValue].
         *
         * @param value The value as a long.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: Long): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps a boolean as a [JsonValue].
         *
         * @param value The value as a boolean.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: Boolean): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps a double as a [JsonValue].
         *
         * @param value The value as a double.
         * @return The [JsonValue] object.
         */
        @JvmStatic
        public fun wrap(value: Double): JsonValue {
            if (value.isInfinite() || value.isNaN()) {
                return NULL
            }

            return wrapOpt(value)
        }

        /**
         * Wraps a [JsonSerializable] object as a [JsonValue].
         *
         * @param value The value as a [JsonSerializable] object.
         * @return The [JsonValue] object.
         */
        public fun wrap(value: JsonSerializable?): JsonValue {
            return wrapOpt(value)
        }

        /**
         * Wraps any valid object into a [JsonValue]. If the object is unable to be wrapped, [JsonValue.NULL]
         * will be returned instead.
         *
         * @param value The object to wrap.
         * @return The object wrapped in a [JsonValue] or [JsonValue.NULL].
         */
        @JvmStatic
        public fun wrapOpt(value: Any?): JsonValue {
            return wrap(value, NULL)
        }

        /**
         * Wraps any valid object into a [JsonValue]. If the object is unable to be wrapped, the default
         * value will be returned. See [wrap] for rules on object wrapping.
         *
         * @param value The object to wrap.
         * @param defaultValue The default value if the object is unable to be wrapped.
         * @return The object wrapped in a [JsonValue] or the default value if the object is unable to be wrapped.
         */
        public fun wrap(value: Any?, defaultValue: JsonValue): JsonValue {
            return try {
                wrap(value)
            } catch (_: JsonException) {
                defaultValue
            }
        }

        /**
         * Wraps any valid object into a [JsonValue].
         *
         *
         * Objects will be wrapped with the following rules:
         *
         *  * [JSONObject.NULL] or null will result in [JsonValue.NULL].
         *  * Collections, arrays, [JSONArray] values will be wrapped into a [JsonList]
         *  * Maps with String keys will be wrapped into a [JsonMap].
         *  * Strings, primitive wrapper objects, [JsonMap], and [JsonList] will be wrapped directly into a [JsonValue]
         *  * Objects that implement [JsonSerializable] will return [JsonSerializable.toJsonValue] or [JsonValue.NULL].
         *  * JsonValues will be unmodified.
         *
         *
         * @param value The object to wrap.
         * @return The object wrapped in a [JsonValue].
         * @throws JsonException If the object is not a supported type or contains an unsupported type.
         */
        @JvmStatic
        @Throws(JsonException::class)
        public fun wrap(value: Any?): JsonValue {
            if (value == null || value === JSONObject.NULL) {
                return NULL
            }

            if (value is JsonValue) {
                return value
            }

            if (value is JsonMap || value is JsonList || value is Boolean || value is Int || value is Long || value is String) {
                return JsonValue(value)
            }

            if (value is JsonSerializable) {
                return value.toJsonValue()
            }

            if (value is Byte || value is Short) {
                return JsonValue((value as Number).toInt())
            }

            if (value is Char) {
                return JsonValue(value.toString())
            }

            if (value is Float) {
                return JsonValue((value as Number).toDouble())
            }

            if (value is Double) {
                val d = value
                if (d.isInfinite() || d.isNaN()) {
                    throw JsonException("Invalid Double value: $d")
                }

                return JsonValue(d)
            }

            if (value is Number) {
                return JsonValue(value.toDouble())
            }

            if (value is java.lang.Number) {
                return JsonValue(value.doubleValue())
            }

            try {
                if (value is JSONArray) {
                    return wrapJSONArray(value)
                }

                if (value is JSONObject) {
                    return wrapJSONObject(value)
                }

                if (value is Collection<*>) {
                    return wrapCollection(value)
                }

                if (value.javaClass.isArray) {
                    return wrapArray(value)
                }

                if (value is Map<*, *>) {
                    return wrapMap(value)
                }
            } catch (exception: JsonException) {
                throw exception
            } catch (exception: Exception) {
                throw JsonException("Failed to wrap value.", exception)
            }

            throw JsonException("Illegal object: $value")
        }

        /**
         * Helper method to wrap an array.
         *
         * @param array The array to wrap.
         * @return The wrapped array.
         * @throws JsonException If the array contains an unwrappable object.
         */
        @Throws(JsonException::class)
        private fun wrapArray(array: Any): JsonValue {
            val length = java.lang.reflect.Array.getLength(array)
            val list: MutableList<JsonValue> = ArrayList(length)

            for (i in 0..<length) {
                val value = java.lang.reflect.Array.get(array, i)
                if (value != null) {
                    list.add(wrap(value))
                }
            }

            return JsonValue(JsonList(list))
        }

        /**
         * Helper method to wrap a collection.
         *
         * @param collection The collection to wrap.
         * @return The wrapped array.
         * @throws JsonException If the collection contains an unwrappable object.
         */
        @Throws(JsonException::class)
        private fun wrapCollection(collection: Collection<*>): JsonValue {
            val list: MutableList<JsonValue> = ArrayList()

            for (obj in collection) {
                obj?.let { list.add(wrap(it)) }
            }

            return JsonValue(JsonList(list))
        }

        /**
         * Helper method to wrap a Map.
         *
         * @param map The map to wrap.
         * @return The wrapped map.
         * @throws JsonException If the collection contains an unwrappable object.
         */
        @Throws(JsonException::class)
        private fun wrapMap(map: Map<*, *>): JsonValue {
            val jsonValueMap: MutableMap<String, JsonValue> = mutableMapOf()

            for ((key, value) in map) {
                if (key !is String) {
                    throw JsonException("Only string map keys are accepted.")
                }

                value?.let { jsonValueMap[key] = wrap(it) }
            }

            return JsonValue(JsonMap(jsonValueMap))
        }

        /**
         * Helper method to wrap a JSONArray.
         *
         * @param jsonArray The JSONArray to wrap.
         * @return The wrapped JSONArray.
         * @throws JsonException If the collection contains an unwrappable object.
         */
        @Throws(JsonException::class)
        private fun wrapJSONArray(jsonArray: JSONArray): JsonValue {
            val list: MutableList<JsonValue> = ArrayList(jsonArray.length())

            for (i in 0..<jsonArray.length()) {
                if (jsonArray.isNull(i)) {
                    continue
                }

                list.add(wrap(jsonArray.opt(i)))
            }

            // Return a JsonValue that contains a JsonList
            return JsonValue(JsonList(list))
        }

        /**
         * Helper method to wrap a JSONObject.
         *
         * @param jsonObject The JSONObject to wrap.
         * @return The wrapped JSONObject.
         * @throws JsonException If the collection contains an unwrappable object.
         */
        @Throws(JsonException::class)
        private fun wrapJSONObject(jsonObject: JSONObject): JsonValue {
            val jsonValueMap: MutableMap<String, JsonValue> = mutableMapOf()

            val iterator = jsonObject.keys()
            while (iterator.hasNext()) {
                val key = iterator.next() as String
                if (jsonObject.isNull(key)) {
                    continue
                }

                jsonValueMap[key] = wrap(jsonObject.opt(key))
            }

            // Return a JsonValue that contains a JsonMap
            return JsonValue(JsonMap(jsonValueMap))
        }

        /**
         * JsonValue parcel creator.
         *
         * @hide
         */
        @JvmField
        public val CREATOR: Parcelable.Creator<JsonValue> = object : Parcelable.Creator<JsonValue> {
            override fun createFromParcel(`in`: Parcel): JsonValue {
                try {
                    return parseString(`in`.readString())
                } catch (e: JsonException) {
                    UALog.e(e, "JsonValue - Unable to create JsonValue from parcel.")
                    return NULL
                }
            }

            override fun newArray(size: Int): Array<JsonValue> {
                return ArrayList<JsonValue>(size).toTypedArray()
            }
        }
    }
}
