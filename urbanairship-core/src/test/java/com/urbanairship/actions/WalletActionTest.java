/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.UrlAllowList;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class WalletActionTest extends BaseTestCase {

    private final ActionArguments testArgs = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://some.example.com");
    private final UrlAllowList urlAllowList = mock(UrlAllowList.class);
    private final WalletAction action = new WalletAction(() -> urlAllowList);

    @Before
    public void setup() {
        // Default the platform to Android
        TestApplication.getApplication().setPlatform(UAirship.ANDROID_PLATFORM);
    }

    /**
     * Test action accepts kitkat+ devices.
     */
    @Test
    public void testAcceptsKitKat() {
        when(urlAllowList.isAllowed(any(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);
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
        when(urlAllowList.isAllowed(eq("https://some.example.com"), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(false);
        assertFalse(action.acceptsArguments(testArgs));
    }

}
