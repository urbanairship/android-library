/* Copyright Airship and Contributors */

package com.urbanairship.iam.custom;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.iam.DisplayContent;
import com.urbanairship.json.JsonException;
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

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(CUSTOM_KEY, value)
                      .build()
                      .toJsonValue();
    }


    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed CustomDisplayContent.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static CustomDisplayContent parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses a json value.
     *
     * @param value The json value.
     * @return A custom display content instance.
     */
    @NonNull
    public static CustomDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
        if (!value.isJsonMap()) {
            throw new JsonException("Invalid custom display content: " + value);
        }

        return new CustomDisplayContent(value.optMap().opt(CUSTOM_KEY));
    }

    /**
     * Gets the custom value.
     *
     * @return The custom value.
     */
    @NonNull
    public JsonValue getValue() {
        return value;
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
