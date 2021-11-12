/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.view.Gravity;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum HorizontalPosition {
    START("start", Gravity.START),
    END("end", Gravity.END),
    CENTER("center", Gravity.CENTER_HORIZONTAL);

    @NonNull
    private final String value;
    private final int gravity;

    HorizontalPosition(@NonNull String value, int gravity) {
        this.value = value;
        this.gravity = gravity;
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

    public int getGravity() {
        return gravity;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
