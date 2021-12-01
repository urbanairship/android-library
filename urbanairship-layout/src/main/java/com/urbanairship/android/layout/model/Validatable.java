/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;

public interface Validatable {
    boolean isRequired();
    boolean isValid();

    static boolean requiredFromJson(@NonNull JsonMap json) {
        return json.opt("required").getBoolean(false);
    }
}
