/* Copyright Airship and Contributors */
package com.urbanairship.util

import java.net.HttpURLConnection
import org.junit.Assert
import org.junit.Test

class UAHttpStatusUtilTest {

    /**
     * Test inSuccessRange succeeds
     */
    @Test
    fun testInSuccessRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inSuccessRange should return true.",
            UAHttpStatusUtil.inSuccessRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inSuccessRange fails
     */
    @Test
    fun testInSuccessRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(HttpURLConnection.HTTP_NOT_FOUND)
        )
    }

    /**
     * Test inSuccessRange fails with 0
     */
    @Test
    fun testInSuccessRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(0)
        )
    }

    /**
     * Test inSuccessRange fails with a negative integer
     */
    @Test
    fun testInSuccessRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inSuccessRange should return false.",
            UAHttpStatusUtil.inSuccessRange(-1)
        )
    }

    /**
     * Test inRedirectionRange succeeds
     */
    @Test
    fun testInRedirectionRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inRedirectionRange should return true.",
            UAHttpStatusUtil.inRedirectionRange(HttpURLConnection.HTTP_MOVED_PERM)
        )
    }

    /**
     * Test inRedirectionRange fails
     */
    @Test
    fun testInRedirectionRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(HttpURLConnection.HTTP_NOT_FOUND)
        )
    }

    /**
     * Test inRedirectionRange fails with 0
     */
    @Test
    fun testInRedirectionRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(0)
        )
    }

    /**
     * Test inRedirectionRange fails with a negative integer
     */
    @Test
    fun testInRedirectionRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inRedirectionRange should return false.",
            UAHttpStatusUtil.inRedirectionRange(-1)
        )
    }

    /**
     * Test inClientErrorRange succeeds
     */
    @Test
    fun testInClientErrorRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inClientErrorRange should return true.",
            UAHttpStatusUtil.inClientErrorRange(HttpURLConnection.HTTP_BAD_REQUEST)
        )
    }

    /**
     * Test inClientErrorRange fails
     */
    @Test
    fun testInClientErrorRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inClientErrorRange fails with 0
     */
    @Test
    fun testInClientErrorRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(0)
        )
    }

    /**
     * Test inClientErrorRange fails with a negative integer
     */
    @Test
    fun testInClientErrorRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inClientErrorRange should return false.",
            UAHttpStatusUtil.inClientErrorRange(-1)
        )
    }

    /**
     * Test inServerErrorRange succeeds
     */
    @Test
    fun testInServerErrorRangePass() {
        Assert.assertTrue(
            "UAHttpStatusUtil inServerErrorRange should return true.",
            UAHttpStatusUtil.inServerErrorRange(HttpURLConnection.HTTP_INTERNAL_ERROR)
        )
    }

    /**
     * Test inServerErrorRange fails
     */
    @Test
    fun testInServerErrorRangeFail() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(HttpURLConnection.HTTP_OK)
        )
    }

    /**
     * Test inServerErrorRange fails with 0
     */
    @Test
    fun testInServerErrorRangeZero() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(0)
        )
    }

    /**
     * Test inServerErrorRange fails with negative integer
     */
    @Test
    fun testInServerErrorRangeNegative() {
        Assert.assertFalse(
            "UAHttpStatusUtil inServerErrorRange should return false.",
            UAHttpStatusUtil.inServerErrorRange(-1)
        )
    }
}
