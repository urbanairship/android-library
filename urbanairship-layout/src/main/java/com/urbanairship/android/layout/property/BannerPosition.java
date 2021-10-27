/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum BannerPosition {
    TOP("top"),
    BOTTOM("bottom");

    @NonNull
    private final String value;

    BannerPosition(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static BannerPosition from(@NonNull String value) {
        for (BannerPosition bp : BannerPosition.values()) {
            if (bp.value.equals(value.toLowerCase(Locale.ROOT))) {
                return bp;
            }
        }
        throw new IllegalArgumentException("Unknown BannerPosition value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
