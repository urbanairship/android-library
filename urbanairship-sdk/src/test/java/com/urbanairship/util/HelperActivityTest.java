/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.util;

import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import junit.framework.Assert;

import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class HelperActivityTest extends BaseTestCase {

    /**
     * Test starting the activity for result.
     */
    @Test
    public void testHandleStartActivityForResult() {
        Intent intent = new Intent("test intent");
        HelperActivity activity = Robolectric.buildActivity(HelperActivity.class)
                                             .withIntent(new Intent().putExtra(HelperActivity.START_ACTIVITY_INTENT_EXTRA, intent))
                                             .create()
                                             .get();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        assertEquals(shadowActivity.getNextStartedActivityForResult().intent, intent);
        assertFalse(activity.isFinishing());
    }

    /**
     * Test starting the activity with no extras no-ops.
     */
    @Test
    public void testHandleStartingActivityNoExtras() {
        HelperActivity activity = Robolectric.buildActivity(HelperActivity.class).create().get();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        assertNull(shadowActivity.getNextStartedActivityForResult());
        assertTrue(activity.isFinishing());
    }

    /**
     * Test starting an activity for a result
     */
    @Test
    public void testStartActivityForResult() throws InterruptedException {
        final Intent activityIntent = new Intent();

        // Set up expected result from the activity
        final int expectedCode = 1;
        final Intent expectedData = new Intent();

        // Run the action in a different thread because starting activity
        // for result blocks
        Thread thread = new Thread(new Runnable() {
            public void run() {
                HelperActivity.ActivityResult result = HelperActivity.startActivityForResult(TestApplication.getApplication().getApplicationContext(), activityIntent);
                Assert.assertEquals("Unexpected result code", expectedCode, result.getResultCode());
                Assert.assertEquals("Unexpected result data", expectedData, result.getIntent());

            }
        });
        thread.start();

        // Wait til we have a started activity from the action thread
        ShadowApplication application = ShadowApplication.getInstance();
        for (int i = 0; i < 10; i++) {
            Intent intent = application.peekNextStartedActivity();
            if (intent != null && intent.getComponent().getClassName().equals(HelperActivity.class.getName())) {
                break;
            }
            Thread.sleep(10);
        }

        // Verify the intent
        Intent intent = application.getNextStartedActivity();
        Assert.assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        Assert.assertEquals(intent.getComponent().getClassName(), HelperActivity.class.getName());
        Assert.assertEquals(activityIntent, intent.getParcelableExtra(HelperActivity.START_ACTIVITY_INTENT_EXTRA));

        ResultReceiver receiver = intent.getParcelableExtra(HelperActivity.RESULT_RECEIVER_EXTRA);
        assertNotNull(receiver);

        // Send the result
        Bundle bundle = new Bundle();
        bundle.putParcelable(HelperActivity.RESULT_INTENT_EXTRA, expectedData);
        receiver.send(expectedCode, bundle);

        // Thread should be able to finish
        thread.join(100);
        Assert.assertFalse("Thread is not finishing", thread.isAlive());
    }
}
