package com.urbanairship.android.layout.util

import org.junit.Assert
import org.junit.Test

class PercentUtilsTest {

    @Test
    fun parsesPercentString() {
        Assert.assertEquals(0.0, PercentUtils.parse("0%").toDouble(), 0.0001)
        Assert.assertEquals(0.0, PercentUtils.parse("0.0%").toDouble(), 0.0001)
        Assert.assertEquals(0.0, PercentUtils.parse("0.00%").toDouble(), 0.0001)

        Assert.assertEquals(0.5, PercentUtils.parse("50%").toDouble(), 0.0001)
        Assert.assertEquals(0.5, PercentUtils.parse("50.0%").toDouble(), 0.0001)
        Assert.assertEquals(0.5, PercentUtils.parse("50.00%").toDouble(), 0.0001)

        Assert.assertEquals(1.0, PercentUtils.parse("100%").toDouble(), 0.0001)
        Assert.assertEquals(1.0, PercentUtils.parse("100.0%").toDouble(), 0.0001)
        Assert.assertEquals(1.0, PercentUtils.parse("100.00%").toDouble(), 0.0001)
    }

    @Test
    fun parsesPercentStringWithoutSymbol() {
        Assert.assertEquals(0.0, PercentUtils.parse("0").toDouble(), 0.0001)
        Assert.assertEquals(0.5, PercentUtils.parse("50").toDouble(), 0.0001)
        Assert.assertEquals(1.0, PercentUtils.parse("100").toDouble(), 0.0001)
    }

    @Test
    fun isPercentMatches() {
        Assert.assertTrue(PercentUtils.isPercent("0%"))
        Assert.assertTrue(PercentUtils.isPercent("50%"))
        Assert.assertTrue(PercentUtils.isPercent("100%"))

        Assert.assertFalse(PercentUtils.isPercent("dog"))
        Assert.assertFalse(PercentUtils.isPercent("9000%"))
        Assert.assertFalse(PercentUtils.isPercent("10.25%"))
        Assert.assertFalse(PercentUtils.isPercent("something 10%"))
    }
}
