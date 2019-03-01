/* Copyright Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
    @Nullable
    String getName(int index);

    /**
     * Gets the string value of the config element.
     *
     * @param index The index of the config element.
     * @return The string value of the config element at the given index.
     */
    @Nullable
    String getString(int index);

    /**
     * Gets the string value of the config element.
     *
     * @param index The index of the config element.
     * @param defaultValue The default value if the string value does not exist at that index.
     * @return The string value of the config element at the given index or the default value.
     */
    @NonNull
    String getString(int index, String defaultValue);

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
    @Nullable
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