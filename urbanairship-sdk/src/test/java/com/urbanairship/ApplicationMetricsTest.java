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

package com.urbanairship;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.analytics.Analytics;

import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ApplicationMetricsTest extends BaseTestCase {

    private ApplicationMetrics metrics;

    @Before
    public void setup() {
        PreferenceDataStore preferenceDataStore = new PreferenceDataStore(TestApplication.getApplication(), mock(UrbanAirshipResolver.class));
        metrics = new ApplicationMetrics(TestApplication.getApplication(), preferenceDataStore);
        metrics.init();
    }

    /**
     * Test last open returns -1 when no opens
     * have been tracked.
     */
    @Test
    public void testGetLastOpenNotSet() {
        assertEquals("Last open time should default to -1", -1, metrics.getLastOpenTimeMillis());
    }

    /**
     * Test when a foreground broadcast is sent the
     * last open time is updated.
     */
    @Test
    public void testLastOpenTimeTracking() {
        // Send the foreground broadcast to update last open time
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Make sure the time is greater than 0
        assertTrue("Last open time should of updated", metrics.getLastOpenTimeMillis() > 0);
    }
}
