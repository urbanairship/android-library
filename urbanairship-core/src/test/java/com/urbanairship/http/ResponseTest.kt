/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import java.util.concurrent.TimeUnit
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ResponseTest {

    @Test
    public fun testNullLocationHeader() {
        val response = Response<Void?>(200, null)
        TestCase.assertNull(response.locationHeader)
    }

    @Test
    public fun testLocationHeader() {
        val headers = mapOf("Location" to "https://fakeLocation.com")

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals("https://fakeLocation.com", response.locationHeader.toString())
    }

    @Test
    public fun testNullRetryAfterHeader() {
        val response = Response<Void?>(200, null)
        TestCase.assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1))
    }

    @Test
    public fun testRetryAfterSeconds() {
        val headers = mapOf("Retry-After" to "120")
        val clock: Clock = TestClock()

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(2, response.getRetryAfterHeader(TimeUnit.MINUTES, -1, clock))
    }

    @Test
    public fun testRetryAfterDate() {
        val clock: Clock = TestClock()

        val futureTimeStamp = DateUtils.createIso8601TimeStamp(clock.currentTimeMillis() + 100000)
        val headers = mapOf("Retry-After" to futureTimeStamp)

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(
            DateUtils.parseIso8601(futureTimeStamp, -1) - clock.currentTimeMillis(),
            response.getRetryAfterHeader(
                TimeUnit.MILLISECONDS, -1, clock
            )
        )
    }

    @Test
    public fun testInvalidRetryAfter() {
        val headers = mapOf("Retry-After" to "what")

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(-1, response.getRetryAfterHeader(TimeUnit.MILLISECONDS, -1))
    }
}
