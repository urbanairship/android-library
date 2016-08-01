/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.Shadows;
import org.robolectric.shadows.support.v4.ShadowLocalBroadcastManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

@SuppressWarnings("ResourceType")
public class AnalyticsTest extends BaseTestCase {

    Analytics analytics;
    ActivityMonitor.Listener activityMonitorListener;
    ActivityMonitor mockActivityMonitor;
    LocalBroadcastManager localBroadcastManager;
    JobDispatcher mockJobDispatcher;


    @Before
    public void setup() {
        mockJobDispatcher = Mockito.mock(JobDispatcher.class);
        mockActivityMonitor = Mockito.mock(ActivityMonitor.class);
        ArgumentCaptor<ActivityMonitor.Listener> listenerCapture = ArgumentCaptor.forClass(ActivityMonitor.Listener.class);

        Mockito.doNothing().when(mockActivityMonitor).setListener(listenerCapture.capture());
        AirshipConfigOptions airshipConfigOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        this.analytics = new Analytics(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore,
                airshipConfigOptions, UAirship.ANDROID_PLATFORM, mockJobDispatcher, mockActivityMonitor);

        analytics.init();

        activityMonitorListener = listenerCapture.getValue();
        assertNotNull("Should set the listener on create", activityMonitorListener);

        localBroadcastManager = LocalBroadcastManager.getInstance(TestApplication.getApplication());
        TestApplication.getApplication().setAnalytics(analytics);
    }

    /**
     * Test that a session id is created when analytics is created
     */
    @Test
    public void testOnCreate() {
        assertNotNull("Session id should generate on create", analytics.getSessionId());
    }

    /**
     * Test that when the app goes into the foreground, a new
     * session id is created, a broadcast is sent for foreground, isForeground
     * is set to true, and a foreground event job is dispatched.
     */
    @Test
    public void testOnForeground() {
        // Start analytics in the background
        activityMonitorListener.onBackground(0);

        assertFalse(analytics.isAppInForeground());

        // Grab the session id to compare it to a new session id
        String sessionId = analytics.getSessionId();

        activityMonitorListener.onForeground(0);

        // Verify that we generate a new session id
        assertNotNull(analytics.getSessionId());
        assertNotSame("A new session id should be generated on foreground", analytics.getSessionId(), sessionId);


        // Verify isAppInForeground is true
        assertTrue(analytics.isAppInForeground());

        // Verify we sent a broadcast intent for app foreground
        ShadowLocalBroadcastManager shadowLocalBroadcastManager = org.robolectric.shadows.support.v4.Shadows.shadowOf(localBroadcastManager);
        List<Intent> broadcasts = shadowLocalBroadcastManager.getSentBroadcastIntents();
        assertEquals("Should of sent a foreground local broadcast",
                broadcasts.get(broadcasts.size() - 1).getAction(), Analytics.ACTION_APP_FOREGROUND);
    }

    /**
     * Test that when the app goes into the background, the conversion send id is
     * cleared, a background event is sent to be added, and a broadcast for background
     * is sent.
     */
    @Test
    public void testOnBackground() {
        // Start analytics in the foreground
        activityMonitorListener.onForeground(0);
        assertTrue(analytics.isAppInForeground());

        analytics.setConversionSendId("some-id");

        activityMonitorListener.onBackground(0);

        // Verify that we clear the conversion send id
        assertNull("App background should clear the conversion send id", analytics.getConversionSendId());

        // Verify that a job to add a background event is dispatched
        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;

                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        AppBackgroundEvent.TYPE.equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
            }
        }));

        // Verify isAppInForeground is false
        assertFalse(analytics.isAppInForeground());

        // Verify we sent a broadcast intent for app background
        ShadowLocalBroadcastManager shadowLocalBroadcastManager = org.robolectric.shadows.support.v4.Shadows.shadowOf(localBroadcastManager);
        List<Intent> broadcasts = shadowLocalBroadcastManager.getSentBroadcastIntents();
        assertEquals("Should of sent a background local broadcast",
                broadcasts.get(broadcasts.size() - 1).getAction(), Analytics.ACTION_APP_BACKGROUND);
    }

    /**
     * Test setting the conversion conversion send id
     */
    @Test
    public void testSetConversionPushId() {
        analytics.setConversionSendId("some-id");
        assertEquals("Conversion send Id is unable to be set", analytics.getConversionSendId(), "some-id");

        analytics.setConversionSendId(null);
        assertNull("Conversion send Id is unable to be cleared", analytics.getConversionSendId());
    }

    /**
     * Test adding an event
     */
    @Test
    public void testAddEvent() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getType()).thenReturn("event-type");
        Mockito.when(event.createEventPayload(Mockito.anyString())).thenReturn("event-data");
        Mockito.when(event.getTime()).thenReturn("1000");
        Mockito.when(event.isValid()).thenReturn(true);
        Mockito.when(event.getPriority()).thenReturn(Event.LOW_PRIORITY);


        analytics.addEvent(event);

        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;

                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        "event-id".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_ID)) &&
                        "event-data".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_DATA)) &&
                        "event-type".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE)) &&
                        "1000".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TIME_STAMP)) &&
                        analytics.getSessionId().equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_SESSION_ID)) &&
                        0 == job.getExtras().getInt(AnalyticsIntentHandler.EXTRA_EVENT_PRIORITY);
            }
        }));
    }

    /**
     * Test adding an event when analytics is disabled through airship config.
     */
    @Test
    public void testAddEventDisabledAnalyticsConfig() {
        AirshipConfigOptions options = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setAnalyticsEnabled(false)
                .build();

        analytics = new Analytics(TestApplication.getApplication(), TestApplication.getApplication().preferenceDataStore, options, UAirship.ANDROID_PLATFORM);

        analytics.addEvent(new AppForegroundEvent(100));
        verifyZeroInteractions(mockJobDispatcher);
    }

    /**
     * Test adding an event when analytics is disabled
     */
    @Test
    public void testAddEventDisabledAnalytics() {
        analytics.setEnabled(false);
        verify(mockJobDispatcher).dispatch(any(Job.class));
        verifyNoMoreInteractions(mockJobDispatcher);

        analytics.addEvent(new AppForegroundEvent(100));
    }

    /**
     * Test adding a null event
     */
    @Test
    public void testAddNullEvent() {
        analytics.addEvent(null);
        verifyZeroInteractions(mockJobDispatcher);
    }

    /**
     * Test adding an invalid event
     */
    @Test
    public void testAddInvalidEvent() {
        Event event = Mockito.mock(Event.class);
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getType()).thenReturn("event-type");
        Mockito.when(event.createEventPayload(Mockito.anyString())).thenReturn("event-data");
        Mockito.when(event.getEventId()).thenReturn("event-id");
        Mockito.when(event.getTime()).thenReturn("1000");
        Mockito.when(event.getPriority()).thenReturn(Event.HIGH_PRIORITY);

        Mockito.when(event.isValid()).thenReturn(false);

        analytics.addEvent(event);
        verifyZeroInteractions(mockJobDispatcher);
    }

    /**
     * Test disabling analytics should start dispatch a job to delete all events.
     */
    @Test
    public void testDisableAnalytics() {
        analytics.setEnabled(false);

        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_DELETE_ALL);
            }
        }));
    }

    /**
     * Test life cycle activity events when an activity is started
     */
    @SuppressLint("NewApi")
    public void testActivityLifeCycleEventsActivityStarted() {
        Activity activity = new Activity();

        TestApplication.getApplication().callback.onActivityStarted(activity);

        // The activity started is posted on the looper
        Shadows.shadowOf(Looper.myLooper()).runToEndOfTasks();

        // Verify that the activity monitor was called with auto instrumentation
        verify(mockActivityMonitor).activityStarted(eq(activity), anyLong());
    }

    /**
     * Test life cycle activity events when an activity is stopped
     */
    @SuppressLint("NewApi")
    public void testActivityLifeCycleEventsActivityStopped() {
        Activity activity = new Activity();

        // The activity stopped is posted on the looper
        TestApplication.getApplication().callback.onActivityStopped(activity);

        // Verify that the activity monitor was called with auto instrumentation
        verify(mockActivityMonitor).activityStopped(eq(activity), anyLong());
    }

    // TODO: Remove this test for 8.0.0 since AssociatedIdentifiers.Builder() has been deprecated
    /**
     * Test associateIdentifiers dispatches a job to add a new associate_identifiers event.
     */
    @Test
    public void testAssociateIdentifiers() {
        analytics.associateIdentifiers(new AssociatedIdentifiers.Builder().create());

        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        "associate_identifiers".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
            }
        }));
    }

    /**
     * Test editAssociatedIdentifiers  dispatches a job to add a new associate_identifiers event.
     */
    @Test
    public void testEditAssociatedIdentifiers() {
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify we started created an add event job
        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        "associate_identifiers".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
            }
        }));

        // Verify identifiers are stored
        AssociatedIdentifiers storedIds = analytics.getAssociatedIdentifiers();
        assertEquals(storedIds.getIds().get("customKey"), "customValue");
        assertEquals(storedIds.getIds().size(), 1);
    }

    /**
     * Test that tracking event adds itself on background
     */
    @Test
    public void testTrackingEventBackground () {
        analytics.setEnabled(true);

        analytics.trackScreen("test_screen");

        // Make call to background
        activityMonitorListener.onBackground(0);

        // Verify we started created an add event job
        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        "screen_tracking".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
            }
        }));
    }

    /**
     * Test that tracking event adds itself upon adding a new screen
     */
    @Test
    public void testTrackingEventAddNewScreen () {
        analytics.trackScreen("test_screen_1");

        // Add another screen
        analytics.trackScreen("test_screen_2");

        // Verify we started created an add event job
        verify(mockJobDispatcher).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_ADD) &&
                        "screen_tracking".equals(job.getExtras().getString(AnalyticsIntentHandler.EXTRA_EVENT_TYPE));
            }
        }));
    }

    /**
     * Test that tracking event ignores duplicate tracking calls for same screen
     */
    @Test
    public void testTrackingEventAddSameScreen() {
        analytics.setEnabled(true);

        analytics.trackScreen("test_screen_1");

        // Add another screen
        analytics.trackScreen("test_screen_1");

        // Verify no jobs were created for the event
        verifyZeroInteractions(mockJobDispatcher);
    }


    /**
     * Test that foregrounding the app with advertising ID tracking enabled dispatches a job to update
     * the advertising ID.
     */
    @Test
    public void testAdIdTrackingOnForeground() {
        // Test the preference settings.
        analytics.setAutoTrackAdvertisingIdEnabled(false);
        assertFalse(analytics.isAutoTrackAdvertisingIdEnabled());

        analytics.setAutoTrackAdvertisingIdEnabled(true);
        assertTrue(analytics.isAutoTrackAdvertisingIdEnabled());

        // Start analytics in the background.
        activityMonitorListener.onBackground(0);
        assertFalse(analytics.isAppInForeground());

        activityMonitorListener.onForeground(0);
        assertTrue(analytics.isAppInForeground());

        // Verify a job was dispatched fot update the advertising ID
        verify(mockJobDispatcher, times(2)).dispatch(Mockito.argThat(new ArgumentMatcher<Job>() {
            @Override
            public boolean matches(Object argument) {
                Job job = (Job) argument;
                return job.getAction().equals(AnalyticsIntentHandler.ACTION_UPDATE_ADVERTISING_ID);
            }
        }));
    }
}
