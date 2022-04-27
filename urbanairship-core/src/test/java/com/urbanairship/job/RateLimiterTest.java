/* Copyright Airship and Contributors */

package com.urbanairship.job;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestClock;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RateLimiterTest extends BaseTestCase {

    final TestClock clock = new TestClock();
    final RateLimiter rateLimiter = new RateLimiter(clock);

    @Test
    public void testRateLimit() {
        rateLimiter.setLimit("foo", 3, 1, TimeUnit.SECONDS);
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());

        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());

        clock.currentTimeMillis += 100;
        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());

        clock.currentTimeMillis += 100;
        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(800, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        clock.currentTimeMillis += 799;
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(1, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        clock.currentTimeMillis += 1;
        assertEquals(rateLimiter.status("foo").getLimitStatus(), RateLimiter.LimitStatus.UNDER);

        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(100, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testStatusNoRule() {
        assertNull(rateLimiter.status("something"));
    }

    @Test
    public void testRateLimitOverTrack() {
        rateLimiter.setLimit("foo", 1, 10, TimeUnit.MILLISECONDS);
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());

        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(10, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        clock.currentTimeMillis += 8;
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(2, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        rateLimiter.track("foo");
        rateLimiter.track("foo");

        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(10, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        clock.currentTimeMillis += 1;
        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(10, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));

        clock.currentTimeMillis += 10;
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());
    }

    @Test
    public void testMultipleRules() {
        rateLimiter.setLimit("foo", 1, 10, TimeUnit.MILLISECONDS);
        rateLimiter.setLimit("bar", 4, 3, TimeUnit.MILLISECONDS);

        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("bar").getLimitStatus());

        rateLimiter.track("foo");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(10, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("bar").getLimitStatus());

        rateLimiter.track("bar");
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(10, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));
        assertEquals(RateLimiter.LimitStatus.UNDER, rateLimiter.status("bar").getLimitStatus());

        rateLimiter.track("bar");
        rateLimiter.track("bar");
        rateLimiter.track("bar");

        clock.currentTimeMillis += 2;

        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(8, rateLimiter.status("foo").getNextAvailable(TimeUnit.MILLISECONDS));
        assertEquals(RateLimiter.LimitStatus.OVER, rateLimiter.status("foo").getLimitStatus());
        assertEquals(1, rateLimiter.status("bar").getNextAvailable(TimeUnit.MILLISECONDS));
    }
}
