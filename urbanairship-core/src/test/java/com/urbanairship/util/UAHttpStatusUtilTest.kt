/* Copyright Airship and Contributors */
package com.urbanairship.util

import java.net.HttpURLConnection
import org.junit.Assert
import org.junit.Test

public class UAHttpStatusUtilTest {

    /**
     * Test inSuccessRange succeeds
     */
    @Test
    public fun testInSuccessRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inSuccessRange should return true.",
            UAHttpStatusUtil.inSuccessRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inSuccessRange fails
     */
    @Test
    public fun testInSuccessRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(HttpURLConnection.HTTP_NOT_FOUND)
        )
    }

    /**
     * Test inSuccessRange fails with 0
     */
    @Test
    public fun testInSuccessRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(0)
        )
    }

    /**
     * Test inSuccessRange fails with a negative integer
     */
    @Test
    public fun testInSuccessRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(-1)
        )
    }

    /**
     * Test inRedirectionRange succeeds
     */
    @Test
    public fun testInRedirectionRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inRedirectionRange should return true.",
            UAHttpStatusUtil.inRedirectionRange(HttpURLConnection.HTTP_MOVED_PERM)
        )
    }

    /**
     * Test inRedirectionRange fails
     */
    @Test
    public fun testInRedirectionRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(HttpURLConnection.HTTP_NOT_FOUND)
        )
    }

    /**
     * Test inRedirectionRange fails with 0
     */
    @Test
    public fun testInRedirectionRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(0)
        )
    }

    /**
     * Test inRedirectionRange fails with a negative integer
     */
    @Test
    public fun testInRedirectionRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(-1)
        )
    }

    /**
     * Test inClientErrorRange succeeds
     */
    @Test
    public fun testInClientErrorRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inClientErrorRange should return true.",
            UAHttpStatusUtil.inClientErrorRange(HttpURLConnection.HTTP_BAD_REQUEST)
        )
    }

    /**
     * Test inClientErrorRange fails
     */
    @Test
    public fun testInClientErrorRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inClientErrorRange fails with 0
     */
    @Test
    public fun testInClientErrorRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(0)
        )
    }

    /**
     * Test inClientErrorRange fails with a negative integer
     */
    @Test
    public fun testInClientErrorRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(-1)
        )
    }

    /**
     * Test inServerErrorRange succeeds
     */
    @Test
    public fun testInServerErrorRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inServerErrorRange should return true.",
            UAHttpStatusUtil.inServerErrorRange(HttpURLConnection.HTTP_INTERNAL_ERROR)
        )
    }

    /**
     * Test inServerErrorRange fails
     */
    @Test
    public fun testInServerErrorRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inServerErrorRange fails with 0
     */
    @Test
    public fun testInServerErrorRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(0)
        )
    }

    /**
     * Test inServerErrorRange fails with negative integer
     */
    @Test
    public fun testInServerErrorRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(-1)
        )
    }
}
