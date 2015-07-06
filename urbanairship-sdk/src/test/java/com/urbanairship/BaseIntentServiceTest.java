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

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowAlarmManager;

import static junit.framework.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class BaseIntentServiceTest extends BaseTestCase {

    /**
     * Test onHandleIntent delegates the work to a delegate.
     */
    @Test
    public void testOnHandleIntent() {
        final BaseIntentService.Delegate mockDelegate = mock(BaseIntentService.Delegate.class);

        BaseIntentService service = new BaseIntentService("test") {
            @Override
            protected Delegate getServiceDelegate(String intentAction, PreferenceDataStore dataStore) {
                assertEquals(UAirship.shared().preferenceDataStore, dataStore);
                assertEquals("TEST_ACTION", intentAction);

                return mockDelegate;
            }
        };

        Intent intent = new Intent("TEST_ACTION");
        service.onHandleIntent(intent);

        // Verify the delegate was handed the work
        verify(mockDelegate).onHandleIntent(intent);
    }

    /**
     * Test onHandleIntent is able to handle a null delegate situation.
     */
    @Test
    public void testOnHandleIntentNullDelegate() {
        BaseIntentService service = new BaseIntentService("test") {
            @Override
            protected Delegate getServiceDelegate(String intentAction, PreferenceDataStore dataStore) {
                assertEquals(UAirship.shared().preferenceDataStore, dataStore);
                assertEquals("TEST_ACTION", intentAction);
                return null;
            }
        };

        // Should not throw an error
        Intent intent = new Intent("TEST_ACTION");
        service.onHandleIntent(intent);
    }

    /**
     * Test retryIntent uses exponential backoff when scheduling the intent.
     */
    @Test
    public void testDelegateRetry() {
        AlarmManager alarmManager = (AlarmManager) RuntimeEnvironment.application.getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadowAlarmManager = Shadows.shadowOf(alarmManager);

        BaseIntentService.Delegate delegate = new BaseIntentService.Delegate(RuntimeEnvironment.application, UAirship.shared().preferenceDataStore) {
            @Override
            protected void onHandleIntent(Intent intent) {
                // Not testing
            }
        };

        // Retry an intent
        Intent intent = new Intent("TEST");
        delegate.retryIntent(intent);

        // Verify the intent is scheduled
        ShadowAlarmManager.ScheduledAlarm alarm = shadowAlarmManager.getNextScheduledAlarm();
        Intent scheduledIntent = Shadows.shadowOf(alarm.operation).getSavedIntent();
        assertEquals("TEST", scheduledIntent.getAction());

        // Verify the intent was scheduled in 10 seconds (initial retry time)
        assertEquals(10000, scheduledIntent.getLongExtra("com.urbanairship.EXTRA_BACK_OFF_MS", 0));

        // Retry it again
        delegate.retryIntent(scheduledIntent);

        // Verify the alarm is set
        alarm = shadowAlarmManager.getNextScheduledAlarm();
        scheduledIntent = Shadows.shadowOf(alarm.operation).getSavedIntent();
        assertEquals("TEST", scheduledIntent.getAction());

        // Verify the intent was scheduled in 20 seconds (initial time * 2)
        assertEquals(20000, scheduledIntent.getLongExtra("com.urbanairship.EXTRA_BACK_OFF_MS", 0));
    }
}