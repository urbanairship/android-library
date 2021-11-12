/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * Used for reporting. Allows us to target in_app experience displays that contain surveys and/or tours.
 */
public enum ContentType {
    SURVEY("survey"),
    TOURS("tours");

    @NonNull
    private final String value;

    ContentType(@NonNull String value) {
        this.value = value;
    }

    @NonNull
    public static ContentType from(@NonNull String value) {
        for (ContentType type : ContentType.values()) {
            if (type.value.equals(value.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown ContentType value: " + value);
    }

    @NonNull
    public static List<ContentType> fromList(@NonNull JsonList json) {
        List<ContentType> contentTypes = new ArrayList<>();
        for (int i = 0; i < json.size(); i++) {
            String typeString = json.get(i).optString();
            ContentType type = ContentType.from(typeString);
        }
        return contentTypes;
    }

    @NonNull
    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
