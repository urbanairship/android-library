/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

import android.support.annotation.NonNull;

public class Clock {

    @NonNull
    public static final Clock DEFAULT_CLOCK = new Clock();

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
