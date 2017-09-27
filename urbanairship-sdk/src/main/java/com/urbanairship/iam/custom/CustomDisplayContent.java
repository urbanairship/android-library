/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.custom;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_CUSTOM} in-app message.
 */
public class CustomDisplayContent implements JsonSerializable {

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
        return value;
    }

    /**
     * Parses a json value.
     *
     * @param jsonValue The json value.
     * @return A custom display content instance.
     */
    public static CustomDisplayContent parseJson(@NonNull JsonValue jsonValue) {
        return new CustomDisplayContent(jsonValue);
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
