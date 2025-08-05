/* Copyright Airship and Contributors */
package com.urbanairship.util

import androidx.annotation.ColorInt

/**
 * Config parser interface.
 *
 * @hide
 */
internal interface ConfigParser {

    /**
     * Count of config elements.
     *
     * @return Count of config elements.
     */
    val count: Int

    /**
     * Gets the name of the config element.
     *
     * @param index The index of the config element.
     * @return The name of the config element.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    @Throws(IndexOutOfBoundsException::class)
    fun getName(index: Int): String?

    /**
     * Gets the string value of the config element.
     *
     * @param name The name of the config element.
     * @return The string value of the config element or null if the element does not exist.
     */
    fun getString(name: String): String?

    /**
     * Gets the string value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value if the string value does not exist at that index.
     * @return The string value of the config element at the given index or the default value if
     * the element does not exist.
     */
    fun getString(name: String, defaultValue: String): String

    /**
     * Gets the boolean value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The boolean value of the config element or the default value if the element does not exist.
     */
    fun getBoolean(name: String, defaultValue: Boolean): Boolean

    /**
     * Gets the string array value of the config element.
     *
     * @param name The name of the config element.
     * @return The string array value of the config element or null if the element does not exist.
     */
    fun getStringArray(name: String): Array<String>?

    /**
     * Gets the resource ID of the config element.
     *
     * @param name The name of the config element.
     * @return The resource ID value of the config element or 0 if the element does not exist.
     */
    fun getDrawableResourceId(name: String): Int

    /**
     * Gets the color value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The color value of the config element or the default value if the element does not exist.
     */
    fun getColor(name: String, @ColorInt defaultValue: Int): Int

    /**
     * Gets the long value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The long value of the config element or the default value if the element does not exist.
     */
    fun getLong(name: String, defaultValue: Long): Long

    /**
     * Gets the int value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The long value of the config element or the default value if the element does not exist.
     */
    fun getInt(name: String, defaultValue: Int): Int

    /**
     * Gets the raw resource ID of the config element.
     *
     * @param name The name of the config element.
     * @return The resource ID value of the config element or 0 if the element does not exist.
     */
    fun getRawResourceId(name: String): Int
}
