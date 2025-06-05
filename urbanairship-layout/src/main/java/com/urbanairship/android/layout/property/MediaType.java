/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum MediaType {
    IMAGE("image"),
    VIDEO("video"),
    YOUTUBE("youtube"),
    VIMEO("vimeo");

    @NonNull
    private final String value;

    MediaType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static MediaType from(@NonNull String value) throws JsonException {
        for (MediaType type : MediaType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown MediaType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
