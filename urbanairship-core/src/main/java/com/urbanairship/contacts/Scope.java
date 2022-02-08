/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Defines the scope of channels.
 */
public enum Scope implements JsonSerializable {

    /**
     * App channels - Android, Amazon, and iOS.
     */
    APP("app"),

    /**
     * Web channels.
     */
    WEB("web"),

    /**
     * EMAIL channels.
     */
    EMAIL("email"),

    /**
     * SMS channels.
     */
    SMS("sms");

    @NonNull
    private final String value;

    Scope(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static Scope fromJson(@NonNull JsonValue value) throws JsonException {
        String valueString = value.optString();
        for (Scope type : Scope.values()) {
            if (type.value.equalsIgnoreCase(valueString)) {
                return type;
            }
        }
        throw new JsonException("Invalid scope: " + value);
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
