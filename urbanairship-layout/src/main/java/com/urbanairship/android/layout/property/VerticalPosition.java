/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum VerticalPosition {
    TOP("top"),
    BOTTOM("bottom"),
    CENTER("center");

    @NonNull
    private final String value;

    VerticalPosition(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static VerticalPosition from(@NonNull String value) {
        for (VerticalPosition vp : VerticalPosition.values()) {
            if (vp.value.equals(value.toLowerCase(Locale.ROOT))) {
                return vp;
            }
        }
        throw new IllegalArgumentException("Unknown VerticalPosition value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
