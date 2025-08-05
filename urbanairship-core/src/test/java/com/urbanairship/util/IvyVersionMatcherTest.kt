/* Copyright Airship and Contributors */
package com.urbanairship.util

import com.urbanairship.BaseTestCase
import org.junit.Assert
import org.junit.Test

/**
 * [IvyVersionMatcher] tests.
 */
class IvyVersionMatcherTest {

    @Test(expected = IllegalArgumentException::class)
    fun testEmptyConstraint() {
        IvyVersionMatcher.newMatcher("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidNumber() {
        IvyVersionMatcher.newMatcher("1.a")
    }

    @Test
    fun testValidVersions() {
        IvyVersionMatcher.newMatcher("[1.22.6.189,)")
        IvyVersionMatcher.newMatcher("[1.22.6.189,2.2.3.4]")
        IvyVersionMatcher.newMatcher("[1.22.6.189, 2.2.3.4]")
        IvyVersionMatcher.newMatcher("[1.22.6.189-junk, 2.2.3.4-junk]")
        IvyVersionMatcher.newMatcher("1.2.3.4")
        IvyVersionMatcher.newMatcher("1.2.3.4.+")
        IvyVersionMatcher.newMatcher("1.2.3-junk")
    }

    @Test
    fun testRangeLongVersion() {
        val matcher = IvyVersionMatcher.newMatcher("[1.22.6.189,)")
        // Should only match the first 3 values
        Assert.assertTrue(matcher.apply("1.22.6"))
        Assert.assertTrue(matcher.apply("1.22.6.189"))
        Assert.assertTrue(matcher.apply("1.22.6.188"))
        Assert.assertTrue(matcher.apply("1.22.7"))
        Assert.assertFalse(matcher.apply("1.22.5"))
    }

    @Test
    fun testRangeWithWhiteSpace() {
        val matcher = IvyVersionMatcher.newMatcher("[ 1.2 , 2.0 ]")

        Assert.assertTrue(matcher.apply("1.2"))
        Assert.assertTrue(matcher.apply("1.2.0"))
        Assert.assertTrue(matcher.apply("1.2.1"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("2.0.0"))

        Assert.assertFalse(matcher.apply("1.1"))
        Assert.assertFalse(matcher.apply("1.1.0"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("2.1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInfiniteStartRangeWithVersion() {
        IvyVersionMatcher.newMatcher("(1.0,2.0]")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInfiniteEndRangeWithVersion() {
        IvyVersionMatcher.newMatcher("[1.0,2.0)")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidStartToken() {
        IvyVersionMatcher.newMatcher("),2.0]")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInvalidEndToken() {
        IvyVersionMatcher.newMatcher("[1.0,2.0(")
    }

    @Test
    fun testExactVersion() {
        var matcher = IvyVersionMatcher.newMatcher("1.0")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0-beta"))
        Assert.assertTrue(matcher.apply("1.0-rc"))
        Assert.assertTrue(matcher.apply("1.0-rc1"))
        Assert.assertTrue(matcher.apply(" 1.0"))
        Assert.assertTrue(matcher.apply("1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0 "))

        matcher = IvyVersionMatcher.newMatcher("1")
        Assert.assertTrue(matcher.apply("1"))
        Assert.assertTrue(matcher.apply("1-SNAPSHOT"))

        Assert.assertFalse(matcher.apply(" 0.9"))
        Assert.assertFalse(matcher.apply("1.1 "))
        Assert.assertFalse(matcher.apply(" 2.0"))
        Assert.assertFalse(matcher.apply(" 2.0 "))

        matcher = IvyVersionMatcher.newMatcher(" 1.0")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply(" 1.0"))
        Assert.assertTrue(matcher.apply("1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0 "))
        Assert.assertTrue(matcher.apply("1.0-alpha "))
        Assert.assertTrue(matcher.apply(" 1.0-beta"))

        Assert.assertFalse(matcher.apply(" 0.9"))
        Assert.assertFalse(matcher.apply("1.1 "))
        Assert.assertFalse(matcher.apply(" 2.0"))
        Assert.assertFalse(matcher.apply(" 2.0 "))

        matcher = IvyVersionMatcher.newMatcher("1.0   ")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply(" 1.0"))
        Assert.assertTrue(matcher.apply("1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0-rc01 "))

        Assert.assertFalse(matcher.apply(" 0.9"))
        Assert.assertFalse(matcher.apply("1.1 "))
        Assert.assertFalse(matcher.apply(" 2.0"))
        Assert.assertFalse(matcher.apply(" 2.0 "))

        matcher = IvyVersionMatcher.newMatcher(" 1.0 ")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply(" 1.0"))
        Assert.assertTrue(matcher.apply("1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0 "))
        Assert.assertTrue(matcher.apply(" 1.0-SNAPSHOT"))

        Assert.assertFalse(matcher.apply(" 0.9"))
        Assert.assertFalse(matcher.apply("1.1 "))
        Assert.assertFalse(matcher.apply(" 2.0"))
        Assert.assertFalse(matcher.apply(" 2.0 "))
    }

    @Test
    fun testSubVersion() {
        var matcher = IvyVersionMatcher.newMatcher("1.0.+")
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.0.5"))
        Assert.assertTrue(matcher.apply("1.0.a"))
        Assert.assertTrue(matcher.apply("1.0.0-SNAPSHOT"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.0"))
        Assert.assertFalse(matcher.apply("1.01"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertFalse(matcher.apply("1.1-beta"))

        matcher = IvyVersionMatcher.newMatcher("1.0+")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1.01-beta"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("2-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher(" 1.0+")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1.01-beta"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("2-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher("1.0+ ")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1.01-beta"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("2-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher(" 1.0+  ")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1.01-beta"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("2-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher("+")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1"))
        Assert.assertTrue(matcher.apply("1.11"))
        Assert.assertTrue(matcher.apply("2"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("2.2.2-beta"))
        Assert.assertTrue(matcher.apply("2-SNAPSHOT"))
    }

    @Test
    fun testVersionRange() {
        var matcher = IvyVersionMatcher.newMatcher("[1.0, 2.0]")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("1.9.9-rc1"))
        Assert.assertTrue(matcher.apply("2.0-beta"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("3.0-alpha"))

        matcher = IvyVersionMatcher.newMatcher("[1.0 ,2.0[")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("1.9.9-rc1"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("2.0"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("2.0-beta"))
        Assert.assertFalse(matcher.apply("3.0-alpha"))

        matcher = IvyVersionMatcher.newMatcher("]1.0 , 2.0]")
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("1.0.1-beta"))
        Assert.assertTrue(matcher.apply("2.0-beta"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("1.0"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("1.0-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher("] 1.0,2.0[")
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("1.0.1-beta"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("1.0"))
        Assert.assertFalse(matcher.apply("2.0"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("2.0-beta"))
        Assert.assertFalse(matcher.apply("3.0-SNAPSHOT"))

        matcher = IvyVersionMatcher.newMatcher("[1.0, )")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("2.0.1"))
        Assert.assertTrue(matcher.apply("3.0"))
        Assert.assertTrue(matcher.apply("999.999.999"))
        Assert.assertTrue(matcher.apply("3.0-SNAPSHOT"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("0.1-rc3"))

        matcher = IvyVersionMatcher.newMatcher("]1.0,) ")
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("2.0.1"))
        Assert.assertTrue(matcher.apply("3.0"))
        Assert.assertTrue(matcher.apply("999.999.999"))
        Assert.assertTrue(matcher.apply("2.0-alpha01"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("1.0"))
        Assert.assertFalse(matcher.apply("1.0-alpha01"))

        matcher = IvyVersionMatcher.newMatcher(" (,2.0]")
        Assert.assertTrue(matcher.apply("0.0"))
        Assert.assertTrue(matcher.apply("0.9.9"))
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("2.0"))
        Assert.assertTrue(matcher.apply("2.0-beta3"))

        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("999.999.999"))
        Assert.assertFalse(matcher.apply("3.0-alpha01"))

        matcher = IvyVersionMatcher.newMatcher(" ( , 2.0 [ ")
        Assert.assertTrue(matcher.apply("0.0"))
        Assert.assertTrue(matcher.apply("0.9.9"))
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("1.1-rc1"))

        Assert.assertFalse(matcher.apply("2.0"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("999.999.999"))
        Assert.assertFalse(matcher.apply("3.0-beta33"))
    }

    @Test
    fun testExactConstraintIgnoresVersionQualifiers() {
        val matcher = IvyVersionMatcher.newMatcher("1.0-beta")
        Assert.assertTrue(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0-alpha01"))
        Assert.assertTrue(matcher.apply("1.0-beta"))
        Assert.assertTrue(matcher.apply("1.0-beta01"))
        Assert.assertTrue(matcher.apply("1.0-rc"))
        Assert.assertTrue(matcher.apply("1.0-rc1"))
        Assert.assertTrue(matcher.apply("1.0"))

        Assert.assertFalse(matcher.apply("1.0.0-SNAPSHOT"))
        Assert.assertFalse(matcher.apply("1.0.0-alpha"))
        Assert.assertFalse(matcher.apply("1.0.0-alpha01"))
        Assert.assertFalse(matcher.apply("1.0.0-beta"))
        Assert.assertFalse(matcher.apply("1.0.0-beta01"))
        Assert.assertFalse(matcher.apply("1.0.0-rc"))
        Assert.assertFalse(matcher.apply("1.0.0-rc1"))
        Assert.assertFalse(matcher.apply("1.0.0"))
    }

    @Test
    fun testVersionRangeIgnoresVersionQualifiers() {
        var matcher = IvyVersionMatcher.newMatcher("[1.0-alpha, 2.0-alpha01]")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.5"))
        Assert.assertTrue(matcher.apply("1.9.9"))
        Assert.assertTrue(matcher.apply("1.9.9-rc1"))
        Assert.assertTrue(matcher.apply("2.0-beta"))
        Assert.assertTrue(matcher.apply("2.0"))

        Assert.assertFalse(matcher.apply("0.0"))
        Assert.assertFalse(matcher.apply("0.9.9"))
        Assert.assertFalse(matcher.apply("2.0.1"))
        Assert.assertFalse(matcher.apply("3.0"))
        Assert.assertFalse(matcher.apply("3.0-alpha"))

        matcher = IvyVersionMatcher.newMatcher("]17.0.0-beta,)")
        Assert.assertFalse(matcher.apply("17.0.0"))
        Assert.assertFalse(matcher.apply("17.0.0-SNAPSHOT"))
        Assert.assertFalse(matcher.apply("17.0.0-alpha"))
        Assert.assertFalse(matcher.apply("17.0.0-beta"))
        Assert.assertFalse(matcher.apply("17.0.0-rc"))

        Assert.assertTrue(matcher.apply("17.0.1"))
        Assert.assertTrue(matcher.apply("17.0.1-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("17.0.1-alpha"))
        Assert.assertTrue(matcher.apply("17.0.1-beta"))
        Assert.assertTrue(matcher.apply("17.0.1-rc"))
        Assert.assertTrue(matcher.apply("18.0.0"))
        Assert.assertTrue(matcher.apply("18.0.0-SNAPSHOT"))
        Assert.assertTrue(matcher.apply("18.0.0-alpha"))
        Assert.assertTrue(matcher.apply("18.0.0-beta"))
        Assert.assertTrue(matcher.apply("999.999.999"))
        Assert.assertTrue(matcher.apply("999.999.999-rc"))
    }

    @Test
    fun testSubVersionIgnoresVersionQualifiers() {
        val matcher = IvyVersionMatcher.newMatcher("1.0-rc1+")
        Assert.assertTrue(matcher.apply("1.0"))
        Assert.assertTrue(matcher.apply("1.0-alpha"))
        Assert.assertTrue(matcher.apply("1.0.1"))
        Assert.assertTrue(matcher.apply("1.00"))
        Assert.assertTrue(matcher.apply("1.01"))
        Assert.assertTrue(matcher.apply("1.01-beta"))

        Assert.assertFalse(matcher.apply("1"))
        Assert.assertFalse(matcher.apply("1.11"))
        Assert.assertFalse(matcher.apply("2"))
        Assert.assertFalse(matcher.apply("2-SNAPSHOT"))
    }

    @Test
    fun testNormalizeVersion() {
        org.junit.Assert.assertNull(IvyVersionMatcher.normalizeVersion(null))
        Assert.assertEquals("", IvyVersionMatcher.normalizeVersion(""))

        Assert.assertEquals("0", IvyVersionMatcher.normalizeVersion("0"))
        Assert.assertEquals("0", IvyVersionMatcher.normalizeVersion("0-beta"))
        Assert.assertEquals("1", IvyVersionMatcher.normalizeVersion("1"))
        Assert.assertEquals("1+", IvyVersionMatcher.normalizeVersion("1+"))
        Assert.assertEquals("1", IvyVersionMatcher.normalizeVersion("1-SNAPSHOT"))
        Assert.assertEquals("1+", IvyVersionMatcher.normalizeVersion("1-SNAPSHOT+"))

        Assert.assertEquals("1.0", IvyVersionMatcher.normalizeVersion("1.0"))
        Assert.assertEquals("1.0", IvyVersionMatcher.normalizeVersion("1.0-rc1"))
        Assert.assertEquals("1.0+", IvyVersionMatcher.normalizeVersion("1.0-rc1+"))
        Assert.assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("1.0.0"))
        Assert.assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("1.0.0-beta"))
        Assert.assertEquals("1.1", IvyVersionMatcher.normalizeVersion("1.1-alpha"))
        Assert.assertEquals("1.1.1+", IvyVersionMatcher.normalizeVersion("1.1.1-alpha+"))

        Assert.assertEquals("1.0.0", IvyVersionMatcher.normalizeVersion("  1.0.0  "))
        Assert.assertEquals("1.1.1", IvyVersionMatcher.normalizeVersion("  1.1.1-alpha  "))
        Assert.assertEquals("1.2.3+", IvyVersionMatcher.normalizeVersion("  1.2.3-alpha+  "))
    }
}
