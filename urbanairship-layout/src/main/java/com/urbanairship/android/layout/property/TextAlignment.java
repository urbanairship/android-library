/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import android.view.Gravity;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

public enum TextAlignment {
    START("start", Gravity.START),
    END("end", Gravity.END),
    CENTER("center", Gravity.CENTER);

    @NonNull
    private final String value;
    private final int gravity;

    TextAlignment(@NonNull String value, int gravity) {
        this.value = value;
        this.gravity = gravity;
    }

    @NonNull
    public static TextAlignment from(@NonNull String value) throws JsonException {
        for (TextAlignment type : TextAlignment.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown Text Alignment value: " + value);
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
