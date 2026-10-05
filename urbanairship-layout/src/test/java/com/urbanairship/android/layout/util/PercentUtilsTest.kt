package com.urbanairship.android.layout.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.property.Size
import com.urbanairship.json.JsonValue
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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
        Assert.assertTrue(PercentUtils.isPercent("9000%"))
        Assert.assertTrue(PercentUtils.isPercent("10.25%"))
        Assert.assertTrue(PercentUtils.isPercent("12.%"))
        Assert.assertTrue(PercentUtils.isPercent(".5%"))

        Assert.assertFalse(PercentUtils.isPercent("dog"))
        Assert.assertFalse(PercentUtils.isPercent("%"))
        Assert.assertFalse(PercentUtils.isPercent("."))
        Assert.assertFalse(PercentUtils.isPercent("-10%"))
        Assert.assertFalse(PercentUtils.isPercent("10"))
        Assert.assertFalse(PercentUtils.isPercent("something 10%"))
    }

    /** A size that isn't a percentage is read as points, so a missed one used to throw on read. */
    @Test
    fun aDecimalPercentageSizeReadsAsAFraction() {
        val size = Size.fromJson(JsonValue.parseString("""{"width": "12.5%", "height": "150%"}"""))
        Assert.assertTrue(size.width.isPercent)
        Assert.assertEquals(0.125, size.width.getFloat().toDouble(), 0.0001)
        Assert.assertEquals(1.5, size.height.getFloat().toDouble(), 0.0001)
    }
}
