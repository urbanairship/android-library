/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.shape;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum ShapeType {
    RECTANGLE("rectangle"),
    CIRCLE("circle");

    @NonNull
    private final String value;

    ShapeType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ShapeType from(@NonNull String value) {
        for (ShapeType type : ShapeType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ShapeType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
