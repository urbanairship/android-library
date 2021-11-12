/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

public enum ButtonClickBehaviorType {
    DISMISS("dismiss"),
    CANCEL("cancel"),
    PAGER_NEXT("pager_next"),
    PAGER_PREVIOUS("pager_previous"),
    FORM_SUBMIT("form_submit");

    @NonNull
    private final String value;

    ButtonClickBehaviorType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ButtonClickBehaviorType from(@NonNull String value) {
        for (ButtonClickBehaviorType type : ButtonClickBehaviorType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ButtonClickBehaviorType value: " + value);
    }

    @NonNull
    public static List<ButtonClickBehaviorType> fromList(@NonNull JsonList json) {
        if (json.isEmpty()) {
            return Collections.emptyList();
        }

        List<ButtonClickBehaviorType> behaviorTypes = new ArrayList<>(json.size());
        for (JsonValue value : json) {
            behaviorTypes.add(from(value.optString()));
        }
        return behaviorTypes;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
