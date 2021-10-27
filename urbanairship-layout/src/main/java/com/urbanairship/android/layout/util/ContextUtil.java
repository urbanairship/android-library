/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ContextUtil {
    private ContextUtil() {}

    @Nullable
    public static Activity getActivityContext(@NonNull Context context) {
        Context ctx = context;
        while (ctx instanceof ContextWrapper) {
            if (ctx instanceof Activity) {
                return (Activity) ctx;
            }
            ctx = ((ContextWrapper) ctx).getBaseContext();
        }
        return null;
    }
}
