/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum ButtonBehavior {
    DISMISS("dismiss"),
    CANCEL("cancel");

    @NonNull
    private final String value;

    ButtonBehavior(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ButtonBehavior from(@NonNull String value) {
        for (ButtonBehavior type : ButtonBehavior.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Button Behavior value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
