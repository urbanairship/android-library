/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;

import com.urbanairship.BaseTestCase;

import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowActivity;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class ActionActivityTest extends BaseTestCase {

    /**
     * Test starting the activity for result.
     */
    @Test
    public void testStartActivityForResult() {
        Intent intent = new Intent("test intent");
        ActionActivity activity = Robolectric.buildActivity(ActionActivity.class)
                                             .withIntent(new Intent().putExtra(ActionActivity.START_ACTIVITY_INTENT_EXTRA, intent))
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
    public void testStartingActivityNoExtras() {
        ActionActivity activity = Robolectric.buildActivity(ActionActivity.class).create().get();

        ShadowActivity shadowActivity = Shadows.shadowOf(activity);
        assertNull(shadowActivity.getNextStartedActivityForResult());
        assertTrue(activity.isFinishing());

    }
}
