/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.view.Gravity;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum VerticalPosition {
    TOP("top", Gravity.TOP),
    BOTTOM("bottom", Gravity.BOTTOM),
    CENTER("center", Gravity.CENTER_VERTICAL);

    @NonNull
    private final String value;
    private final int gravity;

    VerticalPosition(@NonNull String value, int gravity) {
        this.value = value;
        this.gravity = gravity;
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

    public int getGravity() {
        return gravity;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
