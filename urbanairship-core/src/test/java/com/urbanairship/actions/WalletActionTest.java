/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.js.UrlAllowList;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.annotation.Config;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class WalletActionTest extends BaseTestCase {

    private WalletAction action;
    private ActionArguments testArgs;
    private UrlAllowList urlAllowList;

    @Before
    public void setup() {

        action = new WalletAction();
        testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://yep.example.com");

        urlAllowList = UAirship.shared().getUrlAllowList();
        urlAllowList.addEntry("https://yep.example.com");

        // Default the platform to Android
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
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
    public void testRejectsAdmPlatform() {
        TestApplication.getApplication().setPlatform(UAirship.AMAZON_PLATFORM);
        assertFalse(action.acceptsArguments(testArgs));
    }

    /**
     * Test rejects arguments for URLs that are not allowed.
     */
    @Test
    public void testUrlAllowList() {
        testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://nope.example.com");
        assertFalse(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://nope.example.com")));
    }

}
