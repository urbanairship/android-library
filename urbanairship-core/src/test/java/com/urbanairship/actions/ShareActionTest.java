/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

public class ShareActionTest extends BaseTestCase {

    private ShareAction action;
    private ShadowPackageManager packageManager;

    @Before
    public void setup() {
        action = new ShareAction();
        packageManager = shadowOf(RuntimeEnvironment.application.getPackageManager());
    }

    /**
     * Test the share action accepts Strings in foreground situations.
     */
    @Test
    public void testAcceptsArgs() {
        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "share text")));
        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "share text")));
        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "share text")));
        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, "share text")));
        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_AUTOMATION, "share text")));
    }

    /**
     * Test that it rejects Action.SITUATION_PUSH_RECEIVED.
     */
    @Test
    public void testRejectsPossibleBackgroundSituations() {
        assertFalse(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "share text")));
        assertFalse(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, "share text")));
    }

    /**
     * Test perform constructs the correct chooser activity intent.
     */
    @Test
    public void testPerform() {
        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "Share text"));

        // Verify the started intent has the right flags and actions
        Intent startedIntent = shadowOf(RuntimeEnvironment.application).getNextStartedActivity();
        assertEquals(startedIntent.getAction(), Intent.ACTION_CHOOSER);
        assertEquals(startedIntent.getFlags(), Intent.FLAG_ACTIVITY_NEW_TASK);
        assertFalse(startedIntent.hasExtra(Intent.EXTRA_INITIAL_INTENTS));

        // Verify the chooser intent is contained in the starter intent
        Intent chooserIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(chooserIntent.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(chooserIntent.getAction(), Intent.ACTION_SEND);
        assertEquals(null, chooserIntent.getPackage());
        assertFalse(chooserIntent.hasExtra(Intent.EXTRA_INITIAL_INTENTS));
    }

    /**
     * Helper method to create a resolve info with the specified package name.
     *
     * @param packageName The package name.
     * @return The resolve info with the package name.
     */
    private ResolveInfo createResolverInfo(String packageName) {
        ResolveInfo info = new ResolveInfo();
        info.activityInfo = new ActivityInfo();
        info.activityInfo.packageName = packageName;
        info.activityInfo.name = "packageName";
        return info;
    }

}
