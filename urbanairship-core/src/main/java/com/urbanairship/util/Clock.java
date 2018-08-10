/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.util;

public class Clock {

    public static final Clock DEFAULT_CLOCK = new Clock();

    public long currentTimeMillis() {
        return System.currentTimeMillis();
    }
}
