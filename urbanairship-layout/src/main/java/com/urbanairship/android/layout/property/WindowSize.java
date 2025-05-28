/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum WindowSize {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    @NonNull
    private final String value;

    WindowSize(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static WindowSize from(@NonNull String value) throws JsonException {
        for (WindowSize v : WindowSize.values()) {
            if (v.value.equals(value.toLowerCase(Locale.ROOT))) {
                return v;
            }
        }
        throw new JsonException("Unknown WindowSize value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
