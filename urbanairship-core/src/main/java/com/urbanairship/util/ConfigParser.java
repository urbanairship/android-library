/* Copyright Airship and Contributors */

package com.urbanairship.util;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Config parser interface.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ConfigParser {

    /**
     * Count of config elements.
     *
     * @return Count of config elements.
     *
     */
    int getCount();

    /**
     * Gets the name of the config element.
     *
     * @param index The index of the config element.
     * @return The name of the config element.
     * @throws IndexOutOfBoundsException If the index is out of range.
     */
    @Nullable
    String getName(int index) throws IndexOutOfBoundsException;

    /**
     * Gets the string value of the config element.
     *
     * @param name The name of the config element.
     * @return The string value of the config element or null if the element does not exist.
     */
    @Nullable
    String getString(@NonNull String name);

    /**
     * Gets the string value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value if the string value does not exist at that index.
     * @return The string value of the config element at the given index or the default value if
     * the element does not exist.
     */
    @NonNull
    String getString(@NonNull String name, @NonNull String defaultValue);

    /**
     * Gets the boolean value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The boolean value of the config element or the default value if the element does not exist.
     */
    boolean getBoolean(@NonNull String name, boolean defaultValue);

    /**
     * Gets the string array value of the config element.
     *
     * @param name The name of the config element.
     * @return The string array value of the config element or null if the element does not exist.
     */
    @Nullable
    String[] getStringArray(@NonNull String name);

    /**
     * Gets the resource ID of the config element.
     *
     * @param name The name of the config element.
     * @return The resource ID value of the config element or 0 if the element does not exist.
     */
    int getDrawableResourceId(@NonNull String name);

    /**
     * Gets the color value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The color value of the config element or the default value if the element does not exist.
     */
    int getColor(@NonNull String name, @ColorInt int defaultValue);

    /**
     * Gets the long value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The long value of the config element or the default value if the element does not exist.
     */
    long getLong(@NonNull String name, long defaultValue);

    /**
     * Gets the int value of the config element.
     *
     * @param name The name of the config element.
     * @param defaultValue The default value.
     * @return The long value of the config element or the default value if the element does not exist.
     */
    int getInt(@NonNull String name, int defaultValue);

    /**
     * Gets the raw resource ID of the config element.
     *
     * @param name The name of the config element.
     * @return The resource ID value of the config element or 0 if the element does not exist.
     */
    int getRawResourceId(@NonNull String name);
}