/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    public static ButtonEnableBehaviorType from(@NonNull String value) throws JsonException {
        for (ButtonEnableBehaviorType type : ButtonEnableBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown ButtonEnableBehaviorType value: " + value);
    }

    @NonNull
    public static List<ButtonEnableBehaviorType> fromList(@NonNull JsonList json) throws JsonException {
        if (json.isEmpty()) {
            return Collections.emptyList();
        }

        List<ButtonEnableBehaviorType> enableTypes = new ArrayList<>(json.size());
        for (JsonValue value : json) {
            enableTypes.add(from(value.optString()));
        }
        return enableTypes;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
    }
