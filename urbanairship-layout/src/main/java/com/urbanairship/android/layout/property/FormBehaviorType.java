/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum FormBehaviorType {
    SUBMIT_EVENT("submit_event");

    @NonNull
    private final String value;

    FormBehaviorType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static FormBehaviorType from(@NonNull String value) {
        for (FormBehaviorType type : FormBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown Form Behavior Type value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
