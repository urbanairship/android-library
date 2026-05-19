/* Copyright Airship and Contributors */
package com.urbanairship.http

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestClock
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import junit.framework.TestCase
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    @Test
    public fun testRetryAfterCaseInsensitiveHeader() {
        // HTTP header names are case-insensitive (RFC 7230).
        val response = Response<Void?>(200, null, null, mapOf("retry-after" to "120"))
        TestCase.assertEquals(120.seconds, response.getRetryAfterHeader())

        val response2 = Response<Void?>(200, null, null, mapOf("RETRY-AFTER" to "60"))
        TestCase.assertEquals(60.seconds, response2.getRetryAfterHeader())
    }

    @Test
    public fun testLocationCaseInsensitiveHeader() {
        val response = Response<Void?>(200, null, null, mapOf("location" to "https://fakeLocation.com"))
        TestCase.assertEquals("https://fakeLocation.com", response.locationHeader.toString())
    }

    @Test
    public fun testRetryAfterHttpDate() {
        // RFC 7231 §7.1.1.1 IMF-fixdate format.
        val clock = TestClock()
        clock.currentTimeMillis = 1689012646000L // 2023-07-10T18:10:46Z
        val response = Response<Void?>(200, null, null,
            mapOf("Retry-After" to "Mon, 10 Jul 2023 18:11:46 GMT"))
        TestCase.assertEquals(60.seconds, response.getRetryAfterHeader(clock))
    }

    @Test
    public fun testRetryAfterRejectsNegativeNumber() {
        // RFC 7231 §7.1.3 grammar (1*DIGIT) excludes negative values; reject.
        val response = Response<Void?>(200, null, null, mapOf("Retry-After" to "-5"))
        TestCase.assertNull(response.getRetryAfterHeader())
    }

    @Test
    public fun testRetryAfterRejectsScientificNotation() {
        // `1e6` would parse as Double but is outside the RFC delay-seconds grammar.
        val response = Response<Void?>(200, null, null, mapOf("Retry-After" to "1e6"))
        TestCase.assertNull(response.getRetryAfterHeader())
    }

    @Test
    public fun testRetryAfterCoercesPastDateToZero() {
        // A Retry-After date in the past would yield a negative Duration; clamp to zero.
        val clock = TestClock()
        val pastTimeStamp = DateUtils.createIso8601TimeStamp(clock.currentTimeMillis() - 100000)
        val response = Response<Void?>(200, null, null, mapOf("Retry-After" to pastTimeStamp))
        TestCase.assertEquals(Duration.ZERO, response.getRetryAfterHeader(clock))
    }

    @Test
    public fun testRetryAfterTrimsWhitespace() {
        val response = Response<Void?>(200, null, null, mapOf("Retry-After" to "  120  "))
        TestCase.assertEquals(120.seconds, response.getRetryAfterHeader())
    }
}
