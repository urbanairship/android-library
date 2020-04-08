/* Copyright Airship and Contributors */

package com.urbanairship;

import com.urbanairship.util.Clock;

public class TestClock extends Clock {

    public long currentTimeMillis = System.currentTimeMillis();

    @Override
    public long currentTimeMillis() {
        return currentTimeMillis;
    }

}
