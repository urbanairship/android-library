/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.property;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum ButtonEnableBehaviorType {
    FORM_VALIDATION("form_validation"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous");

    @NonNull
    private final String value;

    ButtonEnableBehaviorType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ButtonEnableBehaviorType from(@NonNull String value) {
        for (ButtonEnableBehaviorType type : ButtonEnableBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ButtonEnableBehaviorType value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
    }
