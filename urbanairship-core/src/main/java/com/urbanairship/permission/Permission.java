/* Copyright Airship and Contributors */

package com.urbanairship.permission;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Device permissions.
 */
public enum Permission implements JsonSerializable {
    // Display notifications
    DISPLAY_NOTIFICATIONS("display_notifications"),

    // Access location
    LOCATION("location");

    @NonNull
    private final String value;

    Permission(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public String getValue() {
        return value;
    }

    @NonNull
    public static Permission fromJson(@NonNull JsonValue value) throws JsonException {
        String valueString = value.optString();
        for (Permission type : Permission.values()) {
            if (type.value.equalsIgnoreCase(valueString)) {
                return type;
            }
        }
        throw new JsonException("Invalid permission: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrapOpt(value);
    }
}
