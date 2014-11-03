package com.urbanairship.util;

import com.urbanairship.RobolectricGradleTestRunner;

import org.apache.http.HttpStatus;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class UAHttpStatusUtilTest {

    /**
     * Test inSuccessRange succeeds
     */
    @Test
    public void testInSuccessRangePass() {
        assertTrue("UAHttpStatusUtil inSuccessRange should return true.", UAHttpStatusUtil.inSuccessRange(HttpStatus.SC_OK));
    }

    /**
     * Test inSuccessRange fails
     */
    @Test
    public void testInSuccessRangeFail() {
        assertFalse("UAHttpStatusUtil inSuccessRange should return false.", UAHttpStatusUtil.inSuccessRange(HttpStatus.SC_NOT_FOUND));
    }

    /**
     * Test inSuccessRange fails with 0
     */
    @Test
    public void testInSuccessRangeZero() {
        assertFalse("UAHttpStatusUtil inSuccessRange should return false.", UAHttpStatusUtil.inSuccessRange(0));
    }

    /**
     * Test inSuccessRange fails with a negative integer
     */
    @Test
    public void testInSuccessRangeNegative() {
        assertFalse("UAHttpStatusUtil inSuccessRange should return false.", UAHttpStatusUtil.inSuccessRange(-1));
    }

    /**
     * Test inRedirectionRange succeeds
     */
    @Test
    public void testInRedirectionRangePass() {
        assertTrue("UAHttpStatusUtil inRedirectionRange should return true.", UAHttpStatusUtil.inRedirectionRange(HttpStatus.SC_MOVED_PERMANENTLY));
    }

    /**
     * Test inRedirectionRange fails
     */
    @Test
    public void testInRedirectionRangeFail() {
        assertFalse("UAHttpStatusUtil inRedirectionRange should return false.", UAHttpStatusUtil.inRedirectionRange(HttpStatus.SC_NOT_FOUND));
    }

    /**
     * Test inRedirectionRange fails with 0
     */
    @Test
    public void testInRedirectionRangeZero() {
        assertFalse("UAHttpStatusUtil inRedirectionRange should return false.", UAHttpStatusUtil.inRedirectionRange(0));
    }

    /**
     * Test inRedirectionRange fails with a negative integer
     */
    @Test
    public void testInRedirectionRangeNegative() {
        assertFalse("UAHttpStatusUtil inRedirectionRange should return false.", UAHttpStatusUtil.inRedirectionRange(-1));
    }

    /**
     * Test inClientErrorRange succeeds
     */
    @Test
    public void testInClientErrorRangePass() {
        assertTrue("UAHttpStatusUtil inClientErrorRange should return true.", UAHttpStatusUtil.inClientErrorRange(HttpStatus.SC_BAD_REQUEST));
    }

    /**
     * Test inClientErrorRange fails
     */
    @Test
    public void testInClientErrorRangeFail() {
        assertFalse("UAHttpStatusUtil inClientErrorRange should return false.", UAHttpStatusUtil.inClientErrorRange(HttpStatus.SC_OK));
    }

    /**
     * Test inClientErrorRange fails with 0
     */
    @Test
    public void testInClientErrorRangeZero() {
        assertFalse("UAHttpStatusUtil inClientErrorRange should return false.", UAHttpStatusUtil.inClientErrorRange(0));
    }

    /**
     * Test inClientErrorRange fails with a negative integer
     */
    @Test
    public void testInClientErrorRangeNegative() {
        assertFalse("UAHttpStatusUtil inClientErrorRange should return false.", UAHttpStatusUtil.inClientErrorRange(-1));
    }

    /**
     * Test inServerErrorRange succeeds
     */
    @Test
    public void testInServerErrorRangePass() {
        assertTrue("UAHttpStatusUtil inServerErrorRange should return true.", UAHttpStatusUtil.inServerErrorRange(HttpStatus.SC_INTERNAL_SERVER_ERROR));
    }

    /**
     * Test inServerErrorRange fails
     */
    @Test
    public void testInServerErrorRangeFail() {
        assertFalse("UAHttpStatusUtil inServerErrorRange should return false.", UAHttpStatusUtil.inServerErrorRange(HttpStatus.SC_OK));
    }

    /**
     * Test inServerErrorRange fails with 0
     */
    @Test
    public void testInServerErrorRangeZero() {
        assertFalse("UAHttpStatusUtil inServerErrorRange should return false.", UAHttpStatusUtil.inServerErrorRange(0));
    }

    /**
     * Test inServerErrorRange fails with negative integer
     */
    @Test
    public void testInServerErrorRangeNegative() {
        assertFalse("UAHttpStatusUtil inServerErrorRange should return false.", UAHttpStatusUtil.inServerErrorRange(-1));
    }
}
