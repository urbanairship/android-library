/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;


import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;
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
     * Test perform filters the right packages and constructs the correct chooser activity intent.
     */
    @Test
    public void testPerform() {
        // Create the expected intent that the action will resolve info with
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Share text");

        // Add resolve info for the intent
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("a package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("b package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("c package"));

        // Add in the info for the filtered out packages
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.android.bluetooth"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.android.nfc"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.google.android.apps.docs"));

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "Share text"));

        // Verify the chooser intent has the right flags and actions
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals(startedIntent.getAction(), Intent.ACTION_CHOOSER);
        assertEquals(startedIntent.getFlags(), Intent.FLAG_ACTIVITY_NEW_TASK);

        // The action uses the last resolved intent as the base intent
        Intent chooserIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(chooserIntent.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(chooserIntent.getAction(), Intent.ACTION_SEND);
        assertEquals(chooserIntent.getPackage(), "c package");

        Parcelable[] initialIntents = startedIntent.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS);
        assertEquals(initialIntents.length, 2);

        // Verify the first package
        Intent secondChoice = (Intent) initialIntents[0];
        assertEquals(secondChoice.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(secondChoice.getAction(), Intent.ACTION_SEND);
        assertEquals(secondChoice.getPackage(), "a package");

        // Verify the second package
        Intent thirdChoice = (Intent) initialIntents[1];
        assertEquals(thirdChoice.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(thirdChoice.getAction(), Intent.ACTION_SEND);
        assertEquals(thirdChoice.getPackage(), "b package");
    }

    /**
     * Test share action with no valid share packages still starts the chooser activity.
     */
    @Test
    public void testPerformNoValidPackages() {
        // Create the expected intent that the action will resolve info with
        Intent intent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "Share text");

        // Add resolve info for the intent
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("a package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("b package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("c package"));

        // Create a new share action that excludes all package
        action = new ShareAction() {
            protected boolean excludePackage(String packageName) {
                return true;
            }
        };

        action.perform(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "Share text"));

        // Should still start the intent
        Intent startedIntent = ShadowApplication.getInstance().getNextStartedActivity();
        assertEquals(startedIntent.getAction(), Intent.ACTION_CHOOSER);
        assertEquals(startedIntent.getFlags(), Intent.FLAG_ACTIVITY_NEW_TASK);

        // Verify we have an empty package for the target intent
        Intent chooserIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(chooserIntent.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(chooserIntent.getAction(), Intent.ACTION_SEND);
        assertEquals(chooserIntent.getPackage(), "");

        // Verify we do not have any extra intents
        assertFalse(startedIntent.hasExtra(Intent.EXTRA_INITIAL_INTENTS));
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
