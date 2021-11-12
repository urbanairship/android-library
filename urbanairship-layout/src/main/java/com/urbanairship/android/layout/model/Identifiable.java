/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.model;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public interface Identifiable {
    @NonNull
    String getIdentifier();

    @NonNull
    static String identifierFromJson(@NonNull JsonMap json) throws JsonException {
        String id = json.opt("identifier").getString();
        if (id == null) {
            throw new JsonException("Failed to parse identifier from json: " + json);
        }
        return id;
    }
}
