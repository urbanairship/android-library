/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

/** Types of Score views, used in {@code ScoreStyle}. */
public enum ScoreType {
    NUMBER_RANGE("number_range");

    @NonNull
    private final String value;

    ScoreType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ScoreType from(@NonNull String value) throws JsonException {
        for (ScoreType type : ScoreType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown ScoreType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
