package com.urbanairship.actions;


import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;

import com.urbanairship.RobolectricGradleTestRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.res.builder.RobolectricPackageManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
public class ShareActionTest {

    private ShareAction action;
    private RobolectricPackageManager packageManager;

    @Before
    public void setup() {
        action = new ShareAction();
        packageManager = (RobolectricPackageManager) Robolectric.getShadowApplication().getPackageManager();
    }

    /**
     * Test the share action accepts Strings in foreground situations.
     */
    @Test
    public void testAcceptsArgs() {
        assertTrue(action.acceptsArguments(new ActionArguments(Situation.MANUAL_INVOCATION, "share text")));
        assertTrue(action.acceptsArguments(new ActionArguments(Situation.PUSH_OPENED, "share text")));
        assertTrue(action.acceptsArguments(new ActionArguments(Situation.WEB_VIEW_INVOCATION, "share text")));
        assertTrue(action.acceptsArguments(new ActionArguments(Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON, "share text")));
    }

    /**
     * Test that it rejects Situation.PUSH_RECEIVED.
     */
    @Test
    public void testRejectsPossibleBackgroundSituations() {
        assertFalse(action.acceptsArguments(new ActionArguments(Situation.PUSH_RECEIVED, "share text")));
        assertFalse(action.acceptsArguments(new ActionArguments(Situation.BACKGROUND_NOTIFICATION_ACTION_BUTTON, "share text")));
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
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("first package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("second package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("third package"));

        // Add in the info for the filtered out packages
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.android.bluetooth"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.android.nfc"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("com.google.android.apps.docs"));

        action.perform(null, new ActionArguments(Situation.MANUAL_INVOCATION, "Share text"));

        // Verify the chooser intent has the right flags and actions
        Intent startedIntent = Robolectric.getShadowApplication().getNextStartedActivity();
        assertEquals(startedIntent.getAction(), Intent.ACTION_CHOOSER);
        assertEquals(startedIntent.getFlags(), Intent.FLAG_ACTIVITY_NEW_TASK);

        // The action uses the first resolved intent as the base intent
        Intent chooserIntent = startedIntent.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(chooserIntent.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(chooserIntent.getAction(), Intent.ACTION_SEND);
        assertEquals(chooserIntent.getPackage(), "first package");

        Parcelable[] initialIntents = startedIntent.getParcelableArrayExtra(Intent.EXTRA_INITIAL_INTENTS);
        assertEquals(initialIntents.length, 2);

        // Verify the second package
        Intent secondChoice = (Intent) initialIntents[0];
        assertEquals(secondChoice.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(secondChoice.getAction(), Intent.ACTION_SEND);
        assertEquals(secondChoice.getPackage(), "second package");

        // Verify the third package
        Intent thirdChoice = (Intent) initialIntents[1];
        assertEquals(thirdChoice.getStringExtra(Intent.EXTRA_TEXT), "Share text");
        assertEquals(thirdChoice.getAction(), Intent.ACTION_SEND);
        assertEquals(thirdChoice.getPackage(), "third package");
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
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("first package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("second package"));
        packageManager.addResolveInfoForIntent(intent, createResolverInfo("third package"));

        // Create a new share action that excludes all package
        action = new ShareAction() {
            protected boolean excludePackage(String packageName) {
                return true;
            }
        };

        action.perform(null, new ActionArguments(Situation.MANUAL_INVOCATION, "Share text"));

        // Should still start the intent
        Intent startedIntent = Robolectric.getShadowApplication().getNextStartedActivity();
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
        return info;
    }
}
