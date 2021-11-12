/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.model;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Accessible {
    @Nullable
    String getContentDescription();

    @Nullable
    static String contentDescriptionFromJson(@NonNull JsonMap json) {
        return json.opt("content_description").getString();
    }
}
