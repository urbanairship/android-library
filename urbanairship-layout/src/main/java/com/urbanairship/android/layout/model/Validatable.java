/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.model;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public interface Validatable {
    @Nullable
    Boolean isRequired();

    @Nullable
    static Boolean requiredFromJson(@NonNull JsonMap json) {
        return json.opt("required").getBoolean();
    }
}
