/* Copyright Airship and Contributors */

package com.urbanairship.util;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

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
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));

        matcher = IvyVersionMatcher.newMatcher("1");
        assertTrue(matcher.apply("1"));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher(" 1.0");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher("1.0   ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));

        assertFalse(matcher.apply(" 0.9"));
        assertFalse(matcher.apply("1.1 "));
        assertFalse(matcher.apply(" 2.0"));
        assertFalse(matcher.apply(" 2.0 "));

        matcher = IvyVersionMatcher.newMatcher(" 1.0 ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply(" 1.0"));
        assertTrue(matcher.apply("1.0 "));
        assertTrue(matcher.apply(" 1.0 "));

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

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("1.01"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));

        matcher = IvyVersionMatcher.newMatcher("1.0+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));

        matcher = IvyVersionMatcher.newMatcher(" 1.0+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));

        matcher = IvyVersionMatcher.newMatcher("1.0+ ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));

        matcher = IvyVersionMatcher.newMatcher(" 1.0+  ");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));

        assertFalse(matcher.apply("1"));
        assertFalse(matcher.apply("1.11"));
        assertFalse(matcher.apply("2"));

        matcher = IvyVersionMatcher.newMatcher("+");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.00"));
        assertTrue(matcher.apply("1.01"));
        assertTrue(matcher.apply("1"));
        assertTrue(matcher.apply("1.11"));
        assertTrue(matcher.apply("2"));
    }

    @Test
    public void testVersionRange() {
        IvyVersionMatcher matcher = IvyVersionMatcher.newMatcher("[1.0, 2.0]");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));

        matcher = IvyVersionMatcher.newMatcher("[1.0 ,2.0[");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));

        matcher = IvyVersionMatcher.newMatcher("]1.0 , 2.0]");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));

        matcher = IvyVersionMatcher.newMatcher("] 1.0,2.0[");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));
        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));

        matcher = IvyVersionMatcher.newMatcher("[1.0, )");
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("2.0.1"));
        assertTrue(matcher.apply("3.0"));
        assertTrue(matcher.apply("999.999.999"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));

        matcher = IvyVersionMatcher.newMatcher("]1.0,) ");
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));
        assertTrue(matcher.apply("2.0.1"));
        assertTrue(matcher.apply("3.0"));
        assertTrue(matcher.apply("999.999.999"));

        assertFalse(matcher.apply("0.0"));
        assertFalse(matcher.apply("0.9.9"));
        assertFalse(matcher.apply("1.0"));

        matcher = IvyVersionMatcher.newMatcher(" (,2.0]");
        assertTrue(matcher.apply("0.0"));
        assertTrue(matcher.apply("0.9.9"));
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));
        assertTrue(matcher.apply("2.0"));

        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("999.999.999"));

        matcher = IvyVersionMatcher.newMatcher(" ( , 2.0 [ ");
        assertTrue(matcher.apply("0.0"));
        assertTrue(matcher.apply("0.9.9"));
        assertTrue(matcher.apply("1.0"));
        assertTrue(matcher.apply("1.0.1"));
        assertTrue(matcher.apply("1.5"));
        assertTrue(matcher.apply("1.9.9"));

        assertFalse(matcher.apply("2.0"));
        assertFalse(matcher.apply("2.0.1"));
        assertFalse(matcher.apply("3.0"));
        assertFalse(matcher.apply("999.999.999"));
    }

}