/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum HorizontalPosition {
    START("start"),
    END("end"),
    CENTER("center");

    @NonNull
    private final String value;

    HorizontalPosition(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static HorizontalPosition from(@NonNull String value) {
        for (HorizontalPosition hp : HorizontalPosition.values()) {
            if (hp.value.equals(value.toLowerCase(Locale.ROOT))) {
                return hp;
            }
        }
        throw new IllegalArgumentException("Unknown HorizontalPosition value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
