/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.property.ViewType;

import androidx.annotation.NonNull;

public abstract class BaseModel {
    @NonNull
    private final ViewType viewType;

    public BaseModel(@NonNull ViewType viewType) {
        this.viewType = viewType;
    }

    @NonNull
    public ViewType getType() {
        return viewType;
    }
}
