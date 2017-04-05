/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

// Default to KitKat
@Config(sdk = 19)
public class WalletActionTest extends BaseTestCase {

    private WalletAction action;
    private ActionArguments testArgs;

    @Before
    public void setup() {
        action = new WalletAction();
        testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "http://example.com");

        // Default the platform to Android
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
    }

    /**
     * Test action rejects pre-kitkat devices.
     */
    @Test
    @Config(sdk = 18)
    public void testRejectsPreKitkat() {
        assertFalse(action.acceptsArguments(testArgs));
    }

    /**
     * Test action accepts kitkat+ devices.
     */
    @Test
    public void testAcceptsKitKat() {
        assertTrue(action.acceptsArguments(testArgs));
    }

    /**
     * Test action rejects Amazon/ADM platform.
     */
    @Test
    public void testRejectsAdmPlatfrom() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);

        assertFalse(action.acceptsArguments(testArgs));
    }

}
