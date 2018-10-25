/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Clock {

    @NonNull
    public static final Clock DEFAULT_CLOCK = new Clock();

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
