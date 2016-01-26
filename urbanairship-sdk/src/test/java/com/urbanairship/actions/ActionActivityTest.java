/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
