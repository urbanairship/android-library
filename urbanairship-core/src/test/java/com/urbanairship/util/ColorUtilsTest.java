/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.graphics.Color;

import com.urbanairship.BaseTestCase;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * {@link ColorUtils} tests
 */
public class ColorUtilsTest extends BaseTestCase {

    @Test
    public void testCovertColorString() {
        assertEquals("#ff000000", ColorUtils.convertToString(Color.BLACK));
        assertEquals("#ff0000ff", ColorUtils.convertToString(Color.BLUE));
        assertEquals("#ffff0000", ColorUtils.convertToString(Color.RED));
        assertEquals("#ff00ff00", ColorUtils.convertToString(Color.GREEN));
        assertEquals("#00000000", ColorUtils.convertToString(Color.TRANSPARENT));
    }

}