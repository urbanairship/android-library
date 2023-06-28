/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * {@link IvyVersionMatcher} tests.
 */
public class IvyVersionMatcherTest extends BaseTestCase {

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyConstraint() {
        IvyVersionMatcher.newMatcher("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNumber() {
        IvyVersionMatcher.newMatcher("1.a");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidVersion() {
        IvyVersionMatcher.newMatcher("1.2.3.4");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInfiniteStartRangeWithVersion() {
        IvyVersionMatcher.newMatcher("(1.0,2.0]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInfiniteEndRangeWithVersion() {
        IvyVersionMatcher.newMatcher("[1.0,2.0)");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidStartToken() {
        IvyVersionMatcher.newMatcher("),2.0]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidEndToken() {
        IvyVersionMatcher.newMatcher("[1.0,2.0(");
    }

    @Test
    public void testExactVersion() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("1.0");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-SNAPSHOT"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0-beta"));
        assertTrue(matcher.apply("1.0-rc"));
        assertTrue(matcher.apply("1.0-rc1"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));

        matcher = IvyVersionMatcher.newMatcher("1");
        assertTrue(matcher.apply("1"));
        assertTrue(matcher.apply("1-SNAPSHOT"));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher(" 1.0");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));
        assertTrue(matcher.apply("1.0-alpha "));
        assertTrue(matcher.apply(" 1.0-beta"));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher("1.0   ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));
        assertTrue(matcher.apply(" 1.0-rc01 "));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher(" 1.0 ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));
        assertTrue(matcher.apply(" 1.0-SNAPSHOT"));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));
    }

    @Test
    public void testSubVersion() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("1.0.+");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.0.5"));
        assertTrue(matcher.apply("1.0.a"));
        assertTrue(matcher.apply("1.0.0-SNAPSHOT"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("1.01"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("1.0-SNAPSHOT"));
        assertFalse(matcher.apply("1.1-beta"));

        matcher = IvyVersionMatcher.newMatcher("1.0+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1.01-beta"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("2-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher(" 1.0+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1.01-beta"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("2-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher("1.0+ ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1.01-beta"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("2-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher(" 1.0+  ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1.01-beta"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("2-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher("+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1"));
        assertTrue(matcher.apply("1.11"));
        assertTrue(matcher.apply("2"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("2.2.2-beta"));
        assertTrue(matcher.apply("2-SNAPSHOT"));
    }

    @Test
    public void testVersionRange() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("[1.0, 2.0]");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("1.0-SNAPSHOT"));
        assertTrue(matcher.apply("1.9.9-rc1"));
        assertTrue(matcher.apply("2.0-beta"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("3.0-alpha"));

        matcher = IvyVersionMatcher.newMatcher("[1.0 ,2.0[");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("1.0-SNAPSHOT"));
        assertTrue(matcher.apply("1.9.9-rc1"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("2.0-beta"));
        assertFalse(matcher.apply("3.0-alpha"));

        matcher = IvyVersionMatcher.newMatcher("]1.0 , 2.0]");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("1.0.1-beta"));
        assertTrue(matcher.apply("2.0-beta"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("1.0-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher("] 1.0,2.0[");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("1.0.1-beta"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("2.0-beta"));
        assertFalse(matcher.apply("3.0-SNAPSHOT"));

        matcher = IvyVersionMatcher.newMatcher("[1.0, )");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("2.0.1"));
        assertTrue(matcher.apply("3.0"));
        assertTrue(matcher.apply("999.999.999"));
        assertTrue(matcher.apply("3.0-SNAPSHOT"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("0.1-rc3"));

        matcher = IvyVersionMatcher.newMatcher("]1.0,) ");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("2.0.1"));
        assertTrue(matcher.apply("3.0"));
        assertTrue(matcher.apply("999.999.999"));
        assertTrue(matcher.apply("2.0-alpha01"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("1.0-alpha01"));

        matcher = IvyVersionMatcher.newMatcher(" (,2.0]");
        assertTrue(matcher.apply("0.0"));
        assertTrue(matcher.apply("0.9.9"));
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("2.0-beta3"));

        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("999.999.999"));
        assertFalse(matcher.apply("3.0-alpha01"));

        matcher = IvyVersionMatcher.newMatcher(" ( , 2.0 [ ");
        assertTrue(matcher.apply("0.0"));
        assertTrue(matcher.apply("0.9.9"));
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("1.1-rc1"));

        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("999.999.999"));
        assertFalse(matcher.apply("3.0-beta33"));
    }

    @Test
    public void testExactConstraintIgnoresVersionQualifiers() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("1.0-beta");
        assertTrue(matcher.apply("1.0-SNAPSHOT"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0-alpha01"));
        assertTrue(matcher.apply("1.0-beta"));
        assertTrue(matcher.apply("1.0-beta01"));
        assertTrue(matcher.apply("1.0-rc"));
        assertTrue(matcher.apply("1.0-rc1"));
        assertTrue(matcher.apply("1.0"));

        assertFalse(matcher.apply("1.0.0-SNAPSHOT"));
        assertFalse(matcher.apply("1.0.0-alpha"));
        assertFalse(matcher.apply("1.0.0-alpha01"));
        assertFalse(matcher.apply("1.0.0-beta"));
        assertFalse(matcher.apply("1.0.0-beta01"));
        assertFalse(matcher.apply("1.0.0-rc"));
        assertFalse(matcher.apply("1.0.0-rc1"));
        assertFalse(matcher.apply("1.0.0"));
    }

    @Test
    public void testVersionRangeIgnoresVersionQualifiers() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("[1.0-alpha, 2.0-alpha01]");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-SNAPSHOT"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("1.9.9-rc1"));
        assertTrue(matcher.apply("2.0-beta"));
        assertTrue(matcher.apply("2.0"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("3.0-alpha"));

        matcher = IvyVersionMatcher.newMatcher("]17.0.0-beta,)");
        assertFalse(matcher.apply("17.0.0"));
        assertFalse(matcher.apply("17.0.0-SNAPSHOT"));
        assertFalse(matcher.apply("17.0.0-alpha"));
        assertFalse(matcher.apply("17.0.0-beta"));
        assertFalse(matcher.apply("17.0.0-rc"));

        assertTrue(matcher.apply("17.0.1"));
        assertTrue(matcher.apply("17.0.1-SNAPSHOT"));
        assertTrue(matcher.apply("17.0.1-alpha"));
        assertTrue(matcher.apply("17.0.1-beta"));
        assertTrue(matcher.apply("17.0.1-rc"));
        assertTrue(matcher.apply("18.0.0"));
        assertTrue(matcher.apply("18.0.0-SNAPSHOT"));
        assertTrue(matcher.apply("18.0.0-alpha"));
        assertTrue(matcher.apply("18.0.0-beta"));
        assertTrue(matcher.apply("999.999.999"));
        assertTrue(matcher.apply("999.999.999-rc"));
    }

    @Test
    public void testSubVersionIgnoresVersionQualifiers() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("1.0-rc1+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0-alpha"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1.01-beta"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));
        assertFalse(matcher.apply("2-SNAPSHOT"));
    }

    @Test
    public void testNormalizeVersion() {
        assertNull(IvyVersionMatcher.normalizeVersion(null));
        assertEquals("", IvyVersionMatcher.normalizeVersion(""));

        assertEquals("0", IvyVersionMatcher.normalizeVersion("0"));
        assertEquals("0", IvyVersionMatcher.normalizeVersion("0-beta"));
        assertEquals("1", IvyVersionMatcher.normalizeVersion("1"));
        assertEquals("1+", IvyVersionMatcher.normalizeVersion("1+"));
        assertEquals("1", IvyVersionMatcher.normalizeVersion("1-SNAPSHOT"));
        assertEquals("1+", IvyVersionMatcher.normalizeVersion("1-SNAPSHOT+"));

        assertEquals("1.0", IvyVersionMatcher.normalizeVersion("1.0"));
        assertEquals("1.0", IvyVersionMatcher.normalizeVersion("1.0-rc1"));
        assertEquals("1.0+", IvyVersionMatcher.normalizeVersion("1.0-rc1+"));
        assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("1.0.0"));
        assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("1.0.0-beta"));
        assertEquals("1.1", IvyVersionMatcher.normalizeVersion("1.1-alpha"));
        assertEquals("1.1.1+", IvyVersionMatcher.normalizeVersion("1.1.1-alpha+"));

        assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("  1.0.0  "));
        assertEquals("1.1.1", IvyVersionMatcher.normalizeVersion("  1.1.1-alpha  "));
        assertEquals("1.2.3+", IvyVersionMatcher.normalizeVersion("  1.2.3-alpha+  "));
    }
}
