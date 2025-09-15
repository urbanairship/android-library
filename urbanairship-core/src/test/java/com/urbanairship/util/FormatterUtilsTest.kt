/* Copyright Airship and Contributors */

package com.urbanairship.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.util.FormatterUtils.toSecondsString
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class FormatterUtilsTest {

    @Test
    public fun testIntegerSeconds() {
        Assert.assertEquals("5.00", 5.seconds.toSecondsString())
        Assert.assertEquals("0.00", 0.seconds.toSecondsString())
        Assert.assertEquals("120.00", 120.seconds.toSecondsString())
    }

    @Test
    public fun testOneDecimalPlace() {
        Assert.assertEquals("10.50", 10.5.seconds.toSecondsString())
        Assert.assertEquals("0.10", 0.1.seconds.toSecondsString())
    }

    @Test
    public fun testTwoDecimalPlaces() {
        Assert.assertEquals("75.12", 75.12.seconds.toSecondsString())
        Assert.assertEquals("0.99", 0.99.seconds.toSecondsString())
    }

    @Test
    public fun testRoundingDown() {
        Assert.assertEquals("75.12", 75.123.seconds.toSecondsString())
        Assert.assertEquals("0.33", 0.333333.seconds.toSecondsString())
    }

    @Test
    public fun testRoundingUp() {
        Assert.assertEquals("75.13", 75.129.seconds.toSecondsString())
        Assert.assertEquals("0.67", 0.666666.seconds.toSecondsString())
    }

    @Test
    public fun testRoundingAtHalf() {
        Assert.assertEquals("75.12", 75.125.seconds.toSecondsString())
        Assert.assertEquals("75.13", 75.126.seconds.toSecondsString())
        Assert.assertEquals("0.01", 0.005.seconds.toSecondsString())
        Assert.assertEquals("1.00", 0.999.seconds.toSecondsString()) // Rounds to 1.00
    }

    @Test
    public fun testZeroDuration() {
        Assert.assertEquals("0.00", 0.milliseconds.toSecondsString())
    }

    @Test
    public fun testSmallDurations() {
        Assert.assertEquals("0.12", 123.milliseconds.toSecondsString())
        Assert.assertEquals("0.01", 12.milliseconds.toSecondsString())
        Assert.assertEquals("0.00", 1.milliseconds.toSecondsString()) // Rounds down to 0.00
    }

    @Test
    public fun testConversionFromMinutes() {
        Assert.assertEquals("60.00", 1.minutes.toSecondsString())
        Assert.assertEquals("90.00", 1.5.minutes.toSecondsString()) // 1 minute 30 seconds
        Assert.assertEquals("120.00", 2.minutes.toSecondsString())
    }

    @Test
    public fun testLargeDuration() {
        Assert.assertEquals("3600.00", 3600.seconds.toSecondsString()) // 1 hour
        Assert.assertEquals("86400.00", 86400.seconds.toSecondsString()) // 1 day
    }

    @Test
    public fun testRoundingToNextWholeNumber() {
        Assert.assertEquals("1.00", 0.999.seconds.toSecondsString())
        Assert.assertEquals("2.00", 1.9999.seconds.toSecondsString())
    }

    @Test
    public fun testNegativeValue() {
        Assert.assertEquals("-5.50", (-5.5).seconds.toSecondsString())
    }

    @Test
    public fun testDecimalSeparatorIsDot() {
        Locale.setDefault(Locale.FRENCH)
        val duration = 1.23.seconds
        val formatted = duration.toSecondsString()
        Assert.assertEquals("1.23", formatted)
    }
}
