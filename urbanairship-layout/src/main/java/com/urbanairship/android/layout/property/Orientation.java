/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

public enum Orientation {
    PORTRAIT("portrait"),
    LANDSCAPE("landscape");

    @NonNull
    private final String value;

    Orientation(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static Orientation from(@NonNull String value) throws JsonException {
        for (Orientation o : Orientation.values()) {
            if (o.value.equals(value.toLowerCase(Locale.ROOT))) {
                return o;
            }
        }
        throw new JsonException("Unknown Orientation value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
