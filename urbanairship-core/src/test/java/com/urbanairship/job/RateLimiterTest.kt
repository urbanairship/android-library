/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import com.urbanairship.TestClock
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class RateLimiterTest {

    private val clock: TestClock = TestClock()
    private val rateLimiter: RateLimiter = RateLimiter(clock)

    @Test
    fun testRateLimit() {
        rateLimiter.setLimit("foo", 3, 1.seconds)
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )

        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )

        clock.currentTimeMillis += 100
        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )

        clock.currentTimeMillis += 100
        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(
            800.milliseconds, rateLimiter.status("foo")?.nextAvailable
        )

        clock.currentTimeMillis += 799
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(1.milliseconds, rateLimiter.status("foo")?.nextAvailable)

        clock.currentTimeMillis += 1
        Assert.assertEquals(
            rateLimiter.status("foo")?.limitStatus,
            RateLimiter.LimitStatus.UNDER
        )

        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(
            100.milliseconds, rateLimiter.status("foo")?.nextAvailable
        )
    }

    @Test
    fun testStatusNoRule() {
        Assert.assertNull(rateLimiter.status("something"))
    }

    @Test
    fun testRateLimitOverTrack() {
        rateLimiter.setLimit("foo", 1, 10.milliseconds)
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )

        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(10.milliseconds, rateLimiter.status("foo")?.nextAvailable)

        clock.currentTimeMillis += 8
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(2.milliseconds, rateLimiter.status("foo")?.nextAvailable)

        rateLimiter.track("foo")
        rateLimiter.track("foo")

        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(10.milliseconds, rateLimiter.status("foo")?.nextAvailable)

        clock.currentTimeMillis += 1
        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(10.milliseconds, rateLimiter.status("foo")?.nextAvailable)

        clock.currentTimeMillis += 10
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )
    }

    @Test
    fun testMultipleRules() {
        rateLimiter.setLimit("foo", 1, 10.milliseconds)
        rateLimiter.setLimit("bar", 4, 3.milliseconds)

        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("bar")?.limitStatus
        )

        rateLimiter.track("foo")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(10.milliseconds, rateLimiter.status("foo")?.nextAvailable)
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("bar")?.limitStatus
        )

        rateLimiter.track("bar")
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(10.milliseconds, rateLimiter.status("foo")?.nextAvailable)
        Assert.assertEquals(
            RateLimiter.LimitStatus.UNDER,
            rateLimiter.status("bar")?.limitStatus
        )

        rateLimiter.track("bar")
        rateLimiter.track("bar")
        rateLimiter.track("bar")

        clock.currentTimeMillis += 2

        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(8.milliseconds, rateLimiter.status("foo")?.nextAvailable)
        Assert.assertEquals(
            RateLimiter.LimitStatus.OVER,
            rateLimiter.status("foo")?.limitStatus
        )
        Assert.assertEquals(1.milliseconds, rateLimiter.status("bar")?.nextAvailable)
    }
}
