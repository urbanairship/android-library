/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

/**
 * Config parser interface.
 */
interface ConfigParser {

    /**
     * Count of config elements.
     *
     * @return Count of config elements.
     */
    int getCount();

    /**
     * Gets the name of the config element.
     *
     * @param index The index of the config element.
     * @return The name of the config element at the given index.
     */
    String getName(int index);

    /**
     * Gets the string value of the config element.
     *
     * @param index The index of the config element.
     * @return The string value of the config element at the given index.
     */
    String getString(int index);

    /**
     * Gets the boolean value of the config element.
     *
     * @param index The index of the config element.
     * @return The boolean value of the config element at the given index.
     */
    boolean getBoolean(int index);

    /**
     * Gets the string array value of the config element.
     *
     * @param index The index of the config element.
     * @return The string array value of the config element at the given index.
     */
    String[] getStringArray(int index);

    /**
     * Gets the resource ID of the config element.
     *
     * @param index The index of the config element.
     * @return The resource ID value of the config element at the given index.
     */
    int getDrawableResourceId(int index);

    /**
     * Gets the color value of the config element.
     *
     * @param index The index of the config element.
     * @return The color value of the config element at the given index.
     */
    int getColor(int index);

    /**
     * Gets the long value of the config element.
     *
     * @param index The index of the config element.
     * @return The long value of the config element at the given index.
     */
    long getLong(int index);
}