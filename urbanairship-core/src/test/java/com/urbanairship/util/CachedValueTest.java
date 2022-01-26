/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CachedValueTest extends BaseTestCase {

    private final TestClock clock = new TestClock();
    private final CachedValue<String> cachedValue = new CachedValue<>(clock);

    @Test
    public void testSet() {
        cachedValue.set("some value", 100);
        assertEquals("some value", cachedValue.get());
        cachedValue.set("some other value", 1000);
        assertEquals("some other value", cachedValue.get());

        clock.currentTimeMillis += 999;
        assertEquals("some other value", cachedValue.get());

    }
    @Test
    public void testExpiry() {
        cachedValue.set("some value", 100);
        assertEquals("some value", cachedValue.get());

        clock.currentTimeMillis += 99;
        assertEquals("some value", cachedValue.get());
        clock.currentTimeMillis += 1;
        assertNull(cachedValue.get());
    }

    @Test
    public void testInvalidate() {
        cachedValue.set("some value", 100);
        cachedValue.invalidate();
        assertNull(cachedValue.get());
    }
}
