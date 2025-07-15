/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Parcel
import android.os.Parcelable
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue

/**
 * An ActionValue is a representation of any value that can be described using JSON. It can contain one
 * of the following: a JsonMap, a JsonList, a Number, a Boolean, String, or it can contain null.
 */
public class ActionValue
/**
 * Creates an ActionValue from a JsonValue.
 *
 * @param value A jsonValue.
 */
public constructor(
    value: JsonValue? = null
) : JsonSerializable, Parcelable {

    private val jsonValue: JsonValue = value ?: JsonValue.NULL

    /**
     * Gets the contained value as a String.
     *
     * @return The value as a String, or null if the value is not a String.
     */
    public val string: String? = jsonValue.string

    /**
     * Gets the contained values as a String.
     *
     * @param defaultValue The default value if the contained value is not a String.
     * @return The value as a String, or the defaultValue if the value is not a String.
     */
    public fun getString(defaultValue: String): String {
        return jsonValue.getString(defaultValue)
    }

    /**
     * Gets the contained values as an int.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as an int, or the defaultValue if the value is not a number.
     */
    public fun getInt(defaultValue: Int): Int {
        return jsonValue.getInt(defaultValue)
    }

    /**
     * Gets the contained values as an double.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a double, or the defaultValue if the value is not a number.
     */
    public fun getDouble(defaultValue: Double): Double {
        return jsonValue.getDouble(defaultValue)
    }

    /**
     * Gets the contained values as an long.
     *
     * @param defaultValue The default value if the contained value is not a number.
     * @return The value as a long, or the defaultValue if the value is not a number.
     */
    public fun getLong(defaultValue: Long): Long {
        return jsonValue.getLong(defaultValue)
    }

    /**
     * Gets the contained values as a boolean.
     *
     * @param defaultValue The default value if the contained value is not a boolean.
     * @return The value as a boolean, or the defaultValue if the value is not a boolean.
     */
    public fun getBoolean(defaultValue: Boolean): Boolean {
        return jsonValue.getBoolean(defaultValue)
    }

    /**
     * Gets the contained values as a JsonList.
     *
     * @return The value as a JsonList, or null if the value is not a JsonList.
     */
    public val list: JsonList? = jsonValue.list

    /**
     * Gets the contained values as a JsonMap.
     *
     * @return The value as a JsonMap, or null if the value is not a JsonMap.
     */
    public val map: JsonMap? = jsonValue.map

    /**
     * If the contained value is null.
     *
     * @return `true` if the contained value is null, otherwise `false`.
     */
    public val isNull: Boolean = jsonValue.isNull

    override fun equals(`object`: Any?): Boolean {
        if (`object` is ActionValue) {
            return jsonValue == `object`.jsonValue
        }

        return false
    }

    override fun hashCode(): Int {
        return jsonValue.hashCode()
    }

    /**
     * Returns the ActionValue as a JSON encoded String.
     *
     * @return The value as a JSON encoded String.
     */
    override fun toString(): String {
        return jsonValue.toString()
    }

    override fun toJsonValue(): JsonValue = jsonValue

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(jsonValue, flags)
    }

    public companion object {

        /**
         * Wraps a String as an ActionValue.
         *
         * @param value The action's value as a string.
         * @return The ActionValue object.
         */
        @JvmStatic
        public fun wrap(value: String?): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps an int as an ActionValue.
         *
         * @param value The action's value as an int.
         * @return The ActionValue object.
         */
        public fun wrap(value: Int): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps a long as an ActionValue.
         *
         * @param value The action's value as a long.
         * @return The ActionValue object.
         */
        public fun wrap(value: Long): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps a char as an ActionValue.
         *
         * @param value The action's value as a char.
         * @return The ActionValue object.
         */
        public fun wrap(value: Char): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps a boolean as an ActionValue.
         *
         * @param value The action's value as a boolean.
         * @return The ActionValue object.
         */
        @JvmStatic
        public fun wrap(value: Boolean): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps a JsonSerializable object as an ActionValue.
         *
         * @param value The action's value as a JsonSerializable object.
         * @return The ActionValue object.
         */
        @JvmStatic
        public fun wrap(value: JsonSerializable?): ActionValue {
            return ActionValue(JsonValue.wrap(value))
        }

        /**
         * Wraps a [com.urbanairship.json.JsonValue] compatible object as an ActionValue.
         *
         * @param object The action's value.
         * @return The ActionValue object.
         * @throws com.urbanairship.actions.ActionValueException If the object is unable to be wrapped into an
         * action value.
         */
        @JvmStatic
        @Throws(ActionValueException::class)
        public fun wrap(`object`: Any?): ActionValue {
            try {
                return ActionValue(JsonValue.wrap(`object`))
            } catch (e: JsonException) {
                throw ActionValueException("Invalid ActionValue object: $`object`", e)
            }
        }

        /**
         * ActionValue parcel creator.
         *
         * @hide
         */
        @JvmField
        public val CREATOR: Parcelable.Creator<ActionValue> =
            object : Parcelable.Creator<ActionValue> {
                override fun createFromParcel(`in`: Parcel): ActionValue {
                    return ActionValue(`in`.readParcelable<Parcelable>(JsonValue::class.java.classLoader) as JsonValue?)
                }

                override fun newArray(size: Int): Array<ActionValue> {
                    return arrayOfNulls<ActionValue>(size).map { ActionValue() }.toTypedArray()
                }
            }
    }
}
