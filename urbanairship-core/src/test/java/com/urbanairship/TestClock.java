package com.urbanairship;
/* Copyright 2018 Urban Airship and Contributors */

import com.urbanairship.util.Clock;

public class TestClock extends Clock {

    public long currentTimeMillis = System.currentTimeMillis();

    @Override
    public long currentTimeMillis() {
        return currentTimeMillis;
    }
}
