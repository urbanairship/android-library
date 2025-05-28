/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum Direction {
    VERTICAL("vertical"),
    HORIZONTAL("horizontal");

    @NonNull
    private final String value;

    Direction(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static Direction from(@NonNull String value) throws JsonException {
        for (Direction d : Direction.values()) {
            if (d.value.equals(value.toLowerCase(Locale.ROOT))) {
                return d;
            }
        }
        throw new JsonException("Unknown Direction value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
