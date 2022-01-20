/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

public enum TextStyle {
    BOLD("bold"),
    ITALIC("italic"),
    UNDERLINE("underlined");

    @NonNull
    private final String value;

    TextStyle(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static TextStyle from(@NonNull String value) throws JsonException {
        for (TextStyle type : TextStyle.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown Text Style value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
