/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.js.Whitelist;

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
    private Whitelist whitelist;

    @Before
    public void setup() {

        action = new WalletAction();
        testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://yep.example.com");

        whitelist = UAirship.shared().getWhitelist();
        whitelist.addEntry("https://yep.example.com");
        whitelist.setOpenUrlWhitelistingEnabled(true);

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

    /**
     * Test rejects arguments for URLs that are not whitelisted.
     */
    @Test
    public void testWhiteList() {
        testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://nope.example.com");
        assertFalse(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://nope.example.com")));
    }

}
