/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.custom;

import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_CUSTOM} in-app message.
 */
public class CustomDisplayContent implements DisplayContent {

    private static final String CUSTOM_KEY = "custom";

    private final JsonValue value;

    /**
     * Default constructor.
     *
     * @param value The json payload.
     */
    public CustomDisplayContent(@NonNull JsonValue value) {
        this.value = value;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(CUSTOM_KEY, value)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses a json value.
     *
     * @param jsonValue The json value.
     * @return A custom display content instance.
     */
    public static CustomDisplayContent parseJson(@NonNull JsonValue jsonValue) {
        return new CustomDisplayContent(jsonValue.optMap().opt(CUSTOM_KEY));
    }

    /**
     * Gets the custom value.
     *
     * @return The custom value.
     */
    public JsonValue getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        CustomDisplayContent that = (CustomDisplayContent) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
