/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import junit.framework.TestCase
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
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
        TestCase.assertNull(response.getRetryAfterHeader())
    }

    @Test
    public fun testRetryAfterSeconds() {
        val headers = mapOf("Retry-After" to "120")
        val clock: Clock = TestClock()

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(2.minutes, response.getRetryAfterHeader(clock))
    }

    @Test
    public fun testRetryAfterFractionalSeconds() {
        // RFC 7231 allows decimal seconds.
        val headers = mapOf("Retry-After" to "1.5")
        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(1500.milliseconds, response.getRetryAfterHeader())
    }

    @Test
    public fun testRetryAfterDate() {
        val clock: Clock = TestClock()

        val futureTimeStamp = DateUtils.createIso8601TimeStamp(clock.currentTimeMillis() + 100000)
        val headers = mapOf("Retry-After" to futureTimeStamp)

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertEquals(
            (DateUtils.parseIso8601(futureTimeStamp, -1) - clock.currentTimeMillis()).milliseconds,
            response.getRetryAfterHeader(clock)
        )
    }

    @Test
    public fun testInvalidRetryAfter() {
        val headers = mapOf("Retry-After" to "what")

        val response = Response<Void?>(200, null, null, headers)
        TestCase.assertNull(response.getRetryAfterHeader())
    }
}
