/* Copyright 2016 Urban Airship and Contributors */

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
