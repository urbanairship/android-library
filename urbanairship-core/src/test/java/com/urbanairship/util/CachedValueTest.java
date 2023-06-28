/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import androidx.core.util.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class CachedValueTest extends BaseTestCase {

    private final TestClock clock = new TestClock();
    private final CachedValue<String> cachedValue = new CachedValue<>(clock);


    @Test
    public void testSetDate() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100);
        assertEquals("some value", cachedValue.get());

        clock.currentTimeMillis += 99;
        assertEquals("some value", cachedValue.get());

        clock.currentTimeMillis += 1;
        assertNull(cachedValue.get());
    }

    @Test
    public void testExpiry() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100);
        assertEquals("some value", cachedValue.get());

        clock.currentTimeMillis += 99;
        assertEquals("some value", cachedValue.get());
        clock.currentTimeMillis += 1;
        assertNull(cachedValue.get());
    }

    @Test
    public void testExpireIf() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100);
        assertEquals("some value", cachedValue.get());

        cachedValue.expireIf(s -> s.equals("some other value"));
        assertEquals("some value", cachedValue.get());

        cachedValue.expireIf(s -> s.equals("some value"));
        assertNull(cachedValue.get());
    }

    @Test
    public void testExpire() {
        cachedValue.set("some value", clock.currentTimeMillis() + 100);
        cachedValue.expire();
        assertNull(cachedValue.get());
    }

}
