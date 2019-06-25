/* Copyright Airship and Contributors */

package com.urbanairship.util;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

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
