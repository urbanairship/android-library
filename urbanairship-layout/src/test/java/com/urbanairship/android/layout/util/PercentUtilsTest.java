package com.urbanairship.android.layout.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PercentUtilsTest {

    @Test
    public void parsesPercentString() {
        assertEquals(0f, PercentUtils.parse("0%"), 0.0001);
        assertEquals(0f, PercentUtils.parse("0.0%"), 0.0001);
        assertEquals(0f, PercentUtils.parse("0.00%"), 0.0001);

        assertEquals(0.5f, PercentUtils.parse("50%"), 0.0001);
        assertEquals(0.5f, PercentUtils.parse("50.0%"), 0.0001);
        assertEquals(0.5f, PercentUtils.parse("50.00%"), 0.0001);

        assertEquals(1f, PercentUtils.parse("100%"), 0.0001);
        assertEquals(1f, PercentUtils.parse("100.0%"), 0.0001);
        assertEquals(1f, PercentUtils.parse("100.00%"), 0.0001);
    }

    @Test
    public void parsesPercentStringWithoutSymbol() {
        assertEquals(0f, PercentUtils.parse("0"), 0.0001);
        assertEquals(0.5f, PercentUtils.parse("50"), 0.0001);
        assertEquals(1f, PercentUtils.parse("100"), 0.0001);
    }

    @Test
    public void isPercentMatches() {
        assertTrue(PercentUtils.isPercent("0%"));
        assertTrue(PercentUtils.isPercent("50%"));
        assertTrue(PercentUtils.isPercent("100%"));

        assertFalse(PercentUtils.isPercent("dog"));
        assertFalse(PercentUtils.isPercent("9000%"));
        assertFalse(PercentUtils.isPercent("10.25%"));
        assertFalse(PercentUtils.isPercent("something 10%"));
    }
}
