/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.graphics.Color
import com.urbanairship.util.ColorUtils.convertToString
import org.junit.Assert
import org.junit.Test

/**
 * [ColorUtils] tests
 */
public class ColorUtilsTest {

    @Test
    public fun testCovertColorString() {
        Assert.assertEquals("#ff000000", convertToString(Color.BLACK))
        Assert.assertEquals("#ff0000ff", convertToString(Color.BLUE))
        Assert.assertEquals("#ffff0000", convertToString(Color.RED))
        Assert.assertEquals("#ff00ff00", convertToString(Color.GREEN))
        Assert.assertEquals("#00000000", convertToString(Color.TRANSPARENT))
    }
}
