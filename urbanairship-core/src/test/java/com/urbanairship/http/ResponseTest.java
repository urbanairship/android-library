/* Copyright Airship and Contributors */

package com.urbanairship.http;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;
import com.urbanairship.util.Clock;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class ResponseTest extends BaseTestCase {

    @Test
    public void testNullLocationHeader() {
        Response<Void> response = new Response<>(200, null);
        assertNull(response.getLocationHeader());
    }

    @Test
    public void testLocationHeader() {
        Map<String, String> headers = new HashMap<String, String>() {{
            put("Location", "https://fakeLocation.com");
        }};

        Response<Void> response = new Response<>(200, null, null, headers);
        assertEquals("https://fakeLocation.com", response.getLocationHeader().toString());
    }

    @Test
    public void testNullRetryAfterHeader() {
        Response<Void> response = new Response<>(200, null);
        assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1));
    }

    @Test
    public void testRetryAfterSeconds() {
        Map<String, String> headers = new HashMap<String, String>() {{
            put("Retry-After", "120");
        }};

        Clock clock = new TestClock();

        Response<Void> response = new Response<>(200, null, null, headers);
        assertEquals(2, response.getRetryAfterHeader(TimeUnit.MINUTES, -1, clock));
    }

    @Test
    public void testRetryAfterDate() {
        Clock clock = new TestClock();

        String futureTimeStamp = DateUtils.createIso8601TimeStamp(clock.currentTimeMillis() + 100000);
        Map<String, String> headers = new HashMap<String, String>() {{
            put("Retry-After", futureTimeStamp);
        }};

        Response<Void> response = new Response<>(200, null, null, headers);
        assertEquals(DateUtils.parseIso8601(futureTimeStamp, -1) - clock.currentTimeMillis(), response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1, clock));
    }

    @Test
    public void testInvalidRetryAfter() {
        Map<String, String> headers = new HashMap<String, String>() {{
            put("Retry-After", "what");
        }};

        Response<Void> response = new Response<>(200, null, null, headers);
        assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1));
    }
}
