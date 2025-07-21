/* Copyright Airship and Contributors */
package com.urbanairship.json

import com.urbanairship.UALog
import org.json.JSONException
import org.json.JSONStringer

/**
 * An immutable list of JsonValues.
 */
public class JsonList public constructor(
    list: List<JsonValue>?
) : Iterable<JsonValue>, JsonSerializable {

    private val mutableList: MutableList<JsonValue> = list?.toMutableList() ?: mutableListOf()

    /**
     * Gets the [JsonList] as a List.
     */
    public val list: List<JsonValue>
        get() = mutableList.toList()

    /**
     * Tests whether this `List` contains the specified JSON value.
     *
     * @param jsonValue the object to search for.
     * @return `true` if the list contains the value, otherwise `false`.
     */
    public fun contains(jsonValue: JsonValue): Boolean {
        return mutableList.contains(jsonValue)
    }

    /**
     * Returns the element at the specified location in this `List`.
     *
     * @param location the index of the element to return.
     * @return the element at the specified location.
     * @throws IndexOutOfBoundsException if `location < 0 || location >= size()`
     */
    @kotlin.jvm.Throws(IndexOutOfBoundsException::class)
    public operator fun get(location: Int): JsonValue {
        return mutableList[location]
    }

    /**
     * Searches this `List` for the specified object and returns the index of the
     * first occurrence.
     *
     * @param jsonValue the object to search for.
     * @return the index of the first occurrence of the object or -1 if the
     * object was not found.
     */
    public fun indexOf(jsonValue: JsonValue): Int {
        return mutableList.indexOf(jsonValue)
    }

    /**
     * Returns whether this `List` contains no elements.
     */
    public val isEmpty: Boolean
        get() = mutableList.isEmpty()

    /**
     * Returns an iterator on the elements of this `List`. The elements are
     * iterated in the same order as they occur in the `List`.
     *
     * @return an iterator on the elements of this `List`.
     * @see Iterator
     */
    override fun iterator(): MutableIterator<JsonValue> {
        return mutableList.iterator()
    }

    /**
     * Searches this `List` for the specified object and returns the index of the
     * first occurrence.
     *
     * @param jsonValue the object to search for.
     * @return the index of the first occurrence of the object or -1 if the
     * object was not found.
     */
    public fun lastIndexOf(jsonValue: JsonValue): Int {
        return mutableList.indexOf(jsonValue)
    }

    /**
     * Returns the number of elements in this `List`.
     *
     * @return the number of elements in this `List`.
     */
    public fun size(): Int {
        return mutableList.size
    }

    override fun equals(`object`: Any?): Boolean {
        if (`object` === this) {
            return true
        }

        if ((`object` is JsonList)) {
            return mutableList == `object`.mutableList
        }

        return false
    }

    override fun hashCode(): Int {
        return mutableList.hashCode()
    }

    /**
     * Returns the [JsonList] as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    override fun toString(): String {
        try {
            val stringer = JSONStringer()
            write(stringer, false)
            return stringer.toString()
        } catch (e: JSONException) {
            // Should never happen
            UALog.e(e, "JsonList - Failed to create JSON String.")
            return ""
        } catch (e: StringIndexOutOfBoundsException) {
            UALog.e(e, "JsonList - Failed to create JSON String.")
            return ""
        }
    }

    /**
     * Helper method that is used to write the list as a JSON String.
     *
     * @param stringer The JSONStringer object.
     * @throws org.json.JSONException If the value is unable to be written as JSON.
     */
    @Throws(JSONException::class)
    public fun write(stringer: JSONStringer, sortKeys: Boolean) {
        stringer.array()
        forEach { it.write(stringer, sortKeys) }
        stringer.endArray()
    }

    override fun toJsonValue(): JsonValue {
        return JsonValue.wrap(this)
    }

    public companion object {

        /**
         * Empty list.
         */
        @JvmField
        public val EMPTY_LIST: JsonList = JsonList(null)
    }
}
