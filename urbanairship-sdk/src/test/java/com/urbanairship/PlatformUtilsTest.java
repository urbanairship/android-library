/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class PlatformUtilsTest extends BaseTestCase {

    /**
     * Tests checking if an int is a valid platform value.
     */
    @Test
    public void testIsPlatformValid() {
        // Valid
        assertTrue(PlatformUtils.isPlatformValid(UAirship.AMAZON_PLATFORM));
        assertTrue(PlatformUtils.isPlatformValid(UAirship.ANDROID_PLATFORM));

        // Invalid
        assertFalse(PlatformUtils.isPlatformValid(0));
        assertFalse(PlatformUtils.isPlatformValid(-1));
        assertFalse(PlatformUtils.isPlatformValid(3));
    }

    /**
     * Test parsing a int as a platform int.
     */
    @Test
    public void testParsePlatform() {
        // Valid
        assertEquals(UAirship.AMAZON_PLATFORM, PlatformUtils.parsePlatform(UAirship.AMAZON_PLATFORM));
        assertEquals(UAirship.ANDROID_PLATFORM, PlatformUtils.parsePlatform(UAirship.ANDROID_PLATFORM));

        // Fallback to ANDROID_PLATFORM for invalid values
        assertEquals(UAirship.ANDROID_PLATFORM, PlatformUtils.parsePlatform(0));
        assertEquals(UAirship.ANDROID_PLATFORM, PlatformUtils.parsePlatform(-1));
        assertEquals(UAirship.ANDROID_PLATFORM, PlatformUtils.parsePlatform(3));
    }

    /**
     * Test converting a platform int to a String.
     */
    @Test
    public void testAsString() {
        assertEquals("android", PlatformUtils.asString(UAirship.ANDROID_PLATFORM));
        assertEquals("amazon", PlatformUtils.asString(UAirship.AMAZON_PLATFORM));
    }
}