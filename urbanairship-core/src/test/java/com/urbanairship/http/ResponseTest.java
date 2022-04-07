/* Copyright Airship and Contributors */

package com.urbanairship.http;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;
import com.urbanairship.util.Clock;
import com.urbanairship.util.DateUtils;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;

public class ResponseTest extends BaseTestCase {

    @Test
    public void testNullLocationHeader() {
        Response<Void> response = new Response.Builder<Void>(200).build();
        assertNull(response.getLocationHeader());
    }

    @Test
    public void testLocationHeader() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Location", Collections.singletonList("https://fakeLocation.com"));
        }};

        Response<Void> response = new Response.Builder<Void>(200).setResponseHeaders(headers).build();
        assertEquals("https://fakeLocation.com", response.getLocationHeader().toString());
    }

    @Test
    public void testNullRetryAfterHeader() {
        Response<Void> response = new Response.Builder<Void>(200).build();
        assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1));
    }

    @Test
    public void testRetryAfterSeconds() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Retry-After", Collections.singletonList("120"));
        }};

        Clock clock = new TestClock();

        Response<Void> response = new Response.Builder<Void>(200).setResponseHeaders(headers).build();
        assertEquals(2, response.getRetryAfterHeader(TimeUnit.MINUTES, -1, clock));
    }

    @Test
    public void testRetryAfterDate() {
        Clock clock = new TestClock();

        String futureTimeStamp = DateUtils.createIso8601TimeStamp(clock.currentTimeMillis() + 100000);
        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Retry-After", Collections.singletonList(futureTimeStamp));
        }};

        Response<Void> response = new Response.Builder<Void>(200).setResponseHeaders(headers).build();
        assertEquals(DateUtils.parseIso8601(futureTimeStamp, -1) - clock.currentTimeMillis(), response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1, clock));
    }

    @Test
    public void testInvalidRetryAfter() {
        Map<String, List<String>> headers = new HashMap<String, List<String>>() {{
            put("Retry-After", Collections.singletonList("what"));
        }};

        Response<Void> response = new Response.Builder<Void>(200).setResponseHeaders(headers).build();
        assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1));
    }
}
