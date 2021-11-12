/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public abstract class ToggleStyle {
    @NonNull
    private final ToggleType type;


    ToggleStyle(@NonNull ToggleType type) {
        this.type = type;
    }

    @NonNull
    public static ToggleStyle fromJson(@NonNull JsonMap json) throws JsonException {
        String typeString = json.opt("type").optString();
        switch (ToggleType.from(typeString)) {
            case SWITCH:
                return SwitchStyle.fromJson(json);
            case CHECKBOX:
                return CheckboxStyle.fromJson(json);
        }
        throw new JsonException("Failed to parse ToggleStyle! Unknown type: " + typeString);
    }

    @NonNull
    public ToggleType getType() {
        return type;
    }
}
