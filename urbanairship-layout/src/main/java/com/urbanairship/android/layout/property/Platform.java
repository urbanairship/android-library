/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property;

import com.urbanairship.json.JsonException;

import java.util.Locale;

import androidx.annotation.NonNull;

public enum Platform {
    ANDROID("android"),
    IOS("ios"),
    WEB("web");

    @NonNull
    private final String value;

    Platform(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static Platform from(@NonNull String value) throws JsonException {
        for (Platform type : Platform.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new JsonException("Unknown Platform value: " + value);
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
