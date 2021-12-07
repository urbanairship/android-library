/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.BaseModel;

import androidx.annotation.NonNull;

public interface BaseView<M extends BaseModel> {
    void setModel(@NonNull M model, @NonNull Environment environment);
}
