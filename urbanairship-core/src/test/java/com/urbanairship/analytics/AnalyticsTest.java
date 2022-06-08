/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.os.Build;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class AnalyticsTest extends BaseTestCase {

    private Context context = ApplicationProvider.getApplicationContext();
    private JobDispatcher mockJobDispatcher = mock(JobDispatcher.class);
    private EventManager mockEventManager = mock(EventManager.class);
    private AirshipChannel mockChannel = mock(AirshipChannel.class);
    private PermissionsManager mockPermissionsManager = mock(PermissionsManager.class);

    private PreferenceDataStore dataStore = TestApplication.getApplication().preferenceDataStore;
    private LocaleManager localeManager = new LocaleManager(context, dataStore);
    private Executor executor = Runnable::run;
    private TestAirshipRuntimeConfig runtimeConfig = TestAirshipRuntimeConfig.newTestConfig();
    private TestActivityMonitor activityMonitor = new TestActivityMonitor();
    private PrivacyManager privacyManager = new PrivacyManager(dataStore, PrivacyManager.FEATURE_ALL);

    private Analytics analytics = new Analytics(
            context,
            dataStore,
            runtimeConfig,
            privacyManager,
            mockChannel,
            activityMonitor,
            localeManager,
            executor,
            mockEventManager,
            mockPermissionsManager
    );

    @Before
    public void setup() {
        this.analytics.init();
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
     * session id is created, a broadcast is sent for foreground, isAppForegrounded
     * is set to true, and a foreground event job is dispatched.
     */
    @Test
    public void testOnForeground() {
        // Start analytics in the background
        analytics.onBackground(0);

        assertFalse(analytics.isAppInForeground());

        // Grab the session id to compare it to a new session id
        String sessionId = analytics.getSessionId();

        analytics.onForeground(0);

        // Verify that we generate a new session id
        assertNotNull(analytics.getSessionId());
        assertNotSame("A new session id should be generated on foreground", analytics.getSessionId(), sessionId);
    }

    /**
     * Test that when the app goes into the background, the conversion send id is
     * cleared, a background event is sent to be added, and a broadcast for background
     * is sent.
     */
    @Test
    public void testOnBackground() {
        // Start analytics in the foreground
        analytics.setConversionSendId("some-id");

        analytics.onBackground(0);

        // Verify that we clear the conversion send id
        assertNull("App background should clear the conversion send id", analytics.getConversionSendId());

        // Verify that a job to add a background event is dispatched
        verify(mockEventManager).addEvent(Mockito.any(AppBackgroundEvent.class), Mockito.anyString());
    }

    @Test
    public void testOnBackgroundSchedulesEventUpload() {
        analytics.onBackground(0);
        verify(mockEventManager).scheduleEventUpload(0, TimeUnit.MILLISECONDS);
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
        CustomEvent event = CustomEvent.newBuilder("cool").build();

        analytics.addEvent(event);

        verify(mockEventManager).addEvent(event, analytics.getSessionId());
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

        runtimeConfig.setConfigOptions(options);

        analytics.addEvent(new AppForegroundEvent(100));
        verifyNoInteractions(mockEventManager);
    }

    /**
     * Test adding an event when analytics is disabled
     */
    @Test
    public void testAddEventDisabledAnalytics() {
        analytics.setEnabled(false);
        analytics.addEvent(new AppForegroundEvent(100));
        verify(mockEventManager, never()).addEvent(Mockito.any(AppForegroundEvent.class), Mockito.anyString());
    }

    /**
     * Test adding a null event
     */
    @Test
    public void testAddNullEvent() {
        analytics.addEvent(null);
        verifyNoInteractions(mockEventManager);
    }

    /**
     * Test adding an invalid event
     */
    @Test
    public void testAddInvalidEvent() {
        Event event = mock(Event.class);
        when(event.getEventId()).thenReturn("event-id");
        when(event.getType()).thenReturn("event-type");
        when(event.createEventPayload(Mockito.anyString())).thenReturn("event-data");
        when(event.getEventId()).thenReturn("event-id");
        when(event.getTime()).thenReturn("1000");
        when(event.getPriority()).thenReturn(Event.HIGH_PRIORITY);

        when(event.isValid()).thenReturn(false);

        analytics.addEvent(event);
        verifyNoInteractions(mockJobDispatcher);
    }

    /**
     * Test disabling analytics should start dispatch a job to delete all events.
     */
    @Test
    public void testDisableAnalytics() {
        analytics.setEnabled(false);
        verify(mockEventManager).deleteEvents();
    }

    /**
     * Test editAssociatedIdentifiers dispatches a job to add a new associate_identifiers event.
     */
    @Test
    public void testEditAssociatedIdentifiers() {
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify we started an add event job
        verify(mockEventManager).addEvent(Mockito.any(AssociateIdentifiersEvent.class), Mockito.anyString());

        // Verify identifiers are stored
        AssociatedIdentifiers storedIds = analytics.getAssociatedIdentifiers();
        assertEquals(storedIds.getIds().get("customKey"), "customValue");
        assertEquals(storedIds.getIds().size(), 1);
    }

    /**
     * Test editAssociatedIdentifiers doesn't dispatch a job when adding a duplicate associate_identifier.
     */
    @Test
    public void testEditDuplicateAssociatedIdentifiers() {
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify we started an add event job
        verify(mockEventManager).addEvent(Mockito.any(AssociateIdentifiersEvent.class), Mockito.anyString());

        // Edit with a duplicate identifier
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify we don't add an event more than once
        verify(mockEventManager, times(1)).addEvent(Mockito.any(AssociateIdentifiersEvent.class), Mockito.anyString());
    }

    /**
     * Test that tracking event adds itself on background
     */
    @Test
    public void testTrackingEventBackground() {
        analytics.setEnabled(true);

        analytics.trackScreen("test_screen");

        // Make call to background
        analytics.onBackground(0);

        // Verify we started an add event job
        verify(mockEventManager).addEvent(Mockito.argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event argument) {
                return argument.getEventData().opt("screen").optString().equals("test_screen");
            }
        }), Mockito.anyString());
    }

    /**
     * Test that tracking event adds itself upon adding a new screen
     */
    @Test
    public void testTrackingEventAddNewScreen() {
        analytics.trackScreen("test_screen_1");

        // Add another screen
        analytics.trackScreen("test_screen_2");

        // Verify we started an add event job
        verify(mockEventManager).addEvent(Mockito.argThat(new ArgumentMatcher<Event>() {
            @Override
            public boolean matches(Event argument) {
                return argument.getEventData().opt("screen").optString().equals("test_screen_1");
            }
        }), Mockito.anyString());
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
        verifyNoInteractions(mockJobDispatcher);
    }

    @Test
    public void testEditAssociatedIdentifiersDataCollectionDisabled() {
        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        // Verify identifiers are stored
        AssociatedIdentifiers storedIds = analytics.getAssociatedIdentifiers();
        assertEquals(storedIds.getIds().get("customKey"), "customValue");

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);

        assertTrue(analytics.getAssociatedIdentifiers().getIds().isEmpty());

        analytics.editAssociatedIdentifiers()
                 .addIdentifier("customKey", "customValue")
                 .apply();

        assertTrue(analytics.getAssociatedIdentifiers().getIds().isEmpty());
    }

    @Test
    public void testIsEnabledDataCollectionDisabled() {
        assertTrue(analytics.isEnabled());

        privacyManager.disable(PrivacyManager.FEATURE_ANALYTICS);
        assertFalse(analytics.isEnabled());

        analytics.setEnabled(true);
        assertTrue(privacyManager.isEnabled(PrivacyManager.FEATURE_ANALYTICS));
    }

    /**
     * Tests sending events
     */
    @Test
    public void testSendingEvents() {
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn(null);

        when(mockEventManager.uploadEvents(ArgumentMatchers.<String, String>anyMap())).thenReturn(true);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        assertEquals(JobResult.SUCCESS, analytics.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test sending events when there's no channel ID present
     */
    @Test
    public void testSendingWithNoChannelID() {
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn(null);

        when(mockEventManager.uploadEvents(ArgumentMatchers.<String, String>anyMap())).thenReturn(false);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        assertEquals(JobResult.SUCCESS, analytics.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test sending events when analytics is disabled.
     */
    @Test
    public void testSendingWithAnalyticsDisabled() {
        analytics.setEnabled(false);
        when(mockChannel.getId()).thenReturn("channel");

        when(mockEventManager.uploadEvents(ArgumentMatchers.<String, String>anyMap())).thenReturn(false);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        assertEquals(JobResult.SUCCESS, analytics.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * Test sending events when the upload fails
     */
    @Test
    public void testSendEventsFails() {
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        when(mockEventManager.uploadEvents(ArgumentMatchers.<String, String>anyMap())).thenReturn(false);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        assertEquals(JobResult.RETRY, analytics.onPerformJob(UAirship.shared(), jobInfo));
    }

    /**
     * This verifies all required and most optional headers.
     */
    @Test
    public void testRequestHeaders() {
        localeManager.setLocaleOverride(new Locale("en", "US", "POSIX"));
        analytics.setEnabled(true);
        analytics.registerSDKExtension("cordova", "1.2.3");
        when(mockChannel.getId()).thenReturn("channel");

        String[][] expectedHeaders = new String[][] {
                { "X-UA-Device-Family", "android" },
                { "X-UA-Package-Name", UAirship.getPackageName() },
                { "X-UA-Package-Version", UAirship.getPackageInfo().versionName },
                { "X-UA-App-Key", runtimeConfig.getConfigOptions().appKey },
                { "X-UA-In-Production", Boolean.toString(runtimeConfig.getConfigOptions().inProduction) },
                { "X-UA-Device-Model", Build.MODEL },
                { "X-UA-Android-Version-Code", String.valueOf(Build.VERSION.SDK_INT) },
                { "X-UA-Lib-Version", UAirship.getVersion() },
                { "X-UA-Timezone", TimeZone.getDefault().getID() },
                { "X-UA-Locale-Language", "en" },
                { "X-UA-Locale-Country", "US" },
                { "X-UA-Locale-Variant", "POSIX" },
                { "X-UA-Frameworks", "cordova:1.2.3" },
                { "X-UA-Channel-ID", "channel" }
        };

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();

        for (String[] keyValuePair : expectedHeaders) {
            String actualValue = headers.get(keyValuePair[0]);
            assertEquals(keyValuePair[1], actualValue);
        }
    }

    /**
     * Test that amazon is set as the device family when the platform is amazon.
     */
    @Test
    public void testAmazonDeviceFamily() {
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM);

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertEquals("amazon", headers.get("X-UA-Device-Family"));
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Country if the country
     * field is blank on the locale.
     */
    @Test
    public void testRequestHeaderEmptyLocaleCountryHeaders() {
        localeManager.setLocaleOverride(new Locale("en", "", "POSIX"));

        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertNull(headers.get("X-UA-Locale-Country"));
    }

    /**
     * This verifies that we don't add the X-UA-Locale-Variant if the variant
     * field is blank on the locale.
     */
    @Test
    public void testRequestHeaderEmptyLocaleVariantHeaders() {
        localeManager.setLocaleOverride(new Locale("en", "US", ""));
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertNull(headers.get("X-UA-Locale-Variant"));
    }

    /**
     * This verifies that we don't add any locale fields if the language
     * is empty.
     */
    @Test
    public void testRequestHeaderEmptyLanguageLocaleHeaders() {
        localeManager.setLocaleOverride(new Locale("", "US", "POSIX"));
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertNull(headers.get("X-UA-Locale-Language"));
        assertNull(headers.get("X-UA-Locale-Country"));
        assertNull(headers.get("X-UA-Locale-Variant"));
    }

    /**
     * Test that SDK extensions are registered correctly
     */
    @Test
    public void testSDKExtensions() {
        analytics.registerSDKExtension(Analytics.EXTENSION_CORDOVA, "1.0.0");
        analytics.registerSDKExtension(Analytics.EXTENSION_UNITY, "2.0.0");
        analytics.registerSDKExtension(Analytics.EXTENSION_FLUTTER, "3.0.0");
        analytics.registerSDKExtension(Analytics.EXTENSION_REACT_NATIVE, "4.0.0");
        analytics.registerSDKExtension(Analytics.EXTENSION_XAMARIN, "5.0.0");
        analytics.registerSDKExtension(Analytics.EXTENSION_TITANIUM, "6.0.0");

        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        String expected = "cordova:1.0.0,unity:2.0.0,flutter:3.0.0,react-native:4.0.0,xamarin:5.0.0,titanum:6.0.0";
        assertEquals(expected, headers.get("X-UA-Frameworks"));
    }

    @Test
    public void testPermissionHeaders() {
        when(mockChannel.getId()).thenReturn("channel");

        Map<Permission, PermissionStatus> configuredPermissions = new HashMap<>();
        configuredPermissions.put(Permission.DISPLAY_NOTIFICATIONS, PermissionStatus.GRANTED);
        configuredPermissions.put(Permission.LOCATION, PermissionStatus.NOT_DETERMINED);

        when(mockPermissionsManager.getConfiguredPermissions()).thenReturn(configuredPermissions.keySet());
        when(mockPermissionsManager.checkPermissionStatus(any())).thenAnswer(new Answer<PendingResult<PermissionStatus>>() {
            @Override
            public PendingResult<PermissionStatus> answer(InvocationOnMock invocation) throws Throwable {
                PendingResult<PermissionStatus> pendingResult = new PendingResult<>();
                pendingResult.setResult(configuredPermissions.get(invocation.getArgument(0)));
                return pendingResult;
            }
        });

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertEquals("not_determined", headers.get("X-UA-Permission-location"));
        assertEquals("granted", headers.get("X-UA-Permission-display_notifications"));
    }

    /**
     * Test that analytics header delegates are able to provide additional headers.
     */
    @Test
    public void testAnalyticHeaderDelegate() {
        analytics.setEnabled(true);
        when(mockChannel.getId()).thenReturn("channel");

        analytics.addHeaderDelegate(new Analytics.AnalyticsHeaderDelegate() {
            @NonNull
            @Override
            public Map<String, String> onCreateAnalyticsHeaders() {
                return Collections.singletonMap("foo", "bar");
            }
        });

        analytics.addHeaderDelegate(new Analytics.AnalyticsHeaderDelegate() {
            @NonNull
            @Override
            public Map<String, String> onCreateAnalyticsHeaders() {
                Map<String, String> map = new HashMap<>();
                map.put("cool", "story");
                map.put("neat", "rad");
                return map;
            }
        });

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(EventManager.ACTION_SEND)
                                 .build();

        analytics.onPerformJob(UAirship.shared(), jobInfo);

        ArgumentCaptor<Map<String, String>> argumentCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockEventManager).uploadEvents(argumentCaptor.capture());

        Map<String, String> headers = argumentCaptor.getValue();
        assertEquals("bar", headers.get("foo"));
        assertEquals("story", headers.get("cool"));
        assertEquals("rad", headers.get("neat"));

        // Verify it still includes other headers
        assertEquals("android", headers.get("X-UA-Device-Family"));
    }

}
