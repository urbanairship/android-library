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

public enum EnableBehaviorType {
    FORM_VALIDATION("form_validation"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous"),
    FORM_SUBMISSION("form_submission");

    @NonNull
    private final String value;

    EnableBehaviorType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static EnableBehaviorType from(@NonNull String value) throws JsonException {
        for (EnableBehaviorType type : EnableBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown EnableBehaviorType value: " + value);
    }

    @NonNull
    public static List<EnableBehaviorType> fromList(@NonNull JsonList json) throws JsonException {
        if (json.isEmpty()) {
            return Collections.emptyList();
        }

        List<EnableBehaviorType> enableTypes = new ArrayList<>(json.size());
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
