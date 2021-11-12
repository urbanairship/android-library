/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Defines how a layout should be presented.
 */
public enum PresentationType {
    BANNER("banner"),
    MODAL("modal");

    @NonNull
    private final String value;

    PresentationType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static PresentationType from(@NonNull String value) {
        for (PresentationType type : PresentationType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown PresentationType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
