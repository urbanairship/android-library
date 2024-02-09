/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

import java.util.Locale;

/**
 * Defines how a layout should be presented.
 */
public enum PresentationType {
    BANNER("banner"),
    MODAL("modal"),

    EMBEDDED("embedded");

    @NonNull
    private final String value;

    PresentationType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static PresentationType from(@NonNull String value) throws JsonException {
        for (PresentationType type : PresentationType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown PresentationType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
