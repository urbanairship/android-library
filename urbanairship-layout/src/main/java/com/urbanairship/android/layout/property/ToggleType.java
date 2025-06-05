/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum ToggleType {
    SWITCH("switch"),
    CHECKBOX("checkbox");

    @NonNull
    private final String value;

    ToggleType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ToggleType from(@NonNull String value) throws JsonException {
        for (ToggleType type : ToggleType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown ToggleType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
