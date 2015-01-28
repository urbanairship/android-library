/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.urbanairship.analytics.Analytics;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class ApplicationMetricsTest {

    private Context context;
    private PreferenceDataStore preferenceDataStore;
    private ApplicationMetrics metrics;

    @Before
    public void setup() {
        context = mock(Context.class);
        preferenceDataStore = new PreferenceDataStore(mock(UrbanAirshipResolver.class));
    }

    /**
     * Test last open returns -1 when no opens
     * have been tracked.
     */
    @Test
    public void testGetLastOpenNotSet() {
        metrics = new ApplicationMetrics(context, preferenceDataStore);
        assertEquals("Last open time should default to -1", -1, metrics.getLastOpenTimeMillis());
    }

    /**
     * Test when a foreground broadcast is sent the
     * last open time is updated.
     */
    @Test
    public void testLastOpenTimeTracking() {
        // Set up an argument captor to grab the broadcast receiver
        ArgumentCaptor<BroadcastReceiver> argumentCaptor = ArgumentCaptor.forClass(BroadcastReceiver.class);

        // Only capture it for the right register receiver
        when(context.registerReceiver(argumentCaptor.capture(), argThat(new ArgumentMatcher<IntentFilter>() {
            @Override
            public boolean matches(Object argument) {
                IntentFilter filter = (IntentFilter) argument;
                return filter != null && filter.getAction(0).equals(Analytics.ACTION_APP_FOREGROUND);
            }
        }))).thenReturn(null);

        metrics = new ApplicationMetrics(context, preferenceDataStore);

        // Verify we have a receiver
        BroadcastReceiver receiver = argumentCaptor.getValue();
        assertNotNull("Application metrics should set up a receiver to track foreground events", receiver);

        // Send an app foreground action
        receiver.onReceive(context, new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Make sure the time is greater than 0
        assertTrue("Last open time should of updated", metrics.getLastOpenTimeMillis() > 0);
    }
}
