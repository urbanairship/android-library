/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

import com.urbanairship.json.JsonException;

public enum FormBehaviorType {
    SUBMIT_EVENT("submit_event");

    @NonNull
    private final String value;

    FormBehaviorType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static FormBehaviorType from(@NonNull String value) throws JsonException {
        for (FormBehaviorType type : FormBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown Form Behavior Type value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
