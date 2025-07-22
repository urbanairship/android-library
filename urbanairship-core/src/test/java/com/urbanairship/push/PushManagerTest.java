/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.os.Bundle;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.PrivacyManager;
import com.urbanairship.PushProviders;
import com.urbanairship.R;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestAirshipRuntimeConfig;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.permission.OnPermissionStatusChangedListener;
import com.urbanairship.permission.Permission;
import com.urbanairship.permission.PermissionPromptFallback;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.permission.PermissionsManager;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;

import junit.framework.TestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.core.util.Consumer;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PushManager}.
 */
public class PushManagerTest extends BaseTestCase {

    private PushManager pushManager;
    private PreferenceDataStore preferenceDataStore;
    private PrivacyManager privacyManager;

    private final TestAirshipRuntimeConfig runtimeConfig = new TestAirshipRuntimeConfig();
    private final PushProviders mockPushProviders = mock(PushProviders.class);
    private final JobDispatcher mockDispatcher = mock(JobDispatcher.class);
    private final AirshipChannel mockAirshipChannel = mock(AirshipChannel.class);
    private final PushProvider mockPushProvider = mock(PushProvider.class);
    private final Analytics mockAnalytics = mock(Analytics.class);
    private final PermissionsManager mockPermissionManager = mock(PermissionsManager.class);
    private final Supplier<PushProviders> pushProvidersSupplier = () -> mockPushProviders;
    private final AirshipNotificationManager mockNotificationManager = mock(AirshipNotificationManager.class);
    private final TestActivityMonitor activityMonitor = new TestActivityMonitor();

    private PermissionStatus notificationStatus = PermissionStatus.NOT_DETERMINED;

    @Before
    public void setup() {

        preferenceDataStore = TestApplication.getApplication().getPreferenceDataStore();
        privacyManager = new PrivacyManager(preferenceDataStore, PrivacyManager.Feature.ALL);
        when(mockPushProvider.getDeliveryType()).thenReturn("some type");
        when(mockPushProviders.getBestProvider(anyInt())).thenReturn(mockPushProvider);

        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, runtimeConfig,
                privacyManager, pushProvidersSupplier, mockAirshipChannel, mockAnalytics, mockPermissionManager,
                mockDispatcher, mockNotificationManager, activityMonitor);

        doAnswer(invocation -> {
            Consumer<PermissionStatus> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(notificationStatus);
            return null;
        }).when(mockPermissionManager).checkPermissionStatus(any(), any());
    }

    /**
     * Test init starts push registration if the registration token is not available.
     */
    @Test
    public void testInit() {
        pushManager.init();

        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION)));

        verifyNoMoreInteractions(mockDispatcher);
    }

    /**
     * Test delivery type changes will clear the previous token.
     */
    @Test
    public void testInitClearsPushTokenOnDeliveryChange() throws PushProvider.RegistrationException {
        // Register for a token
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        when(mockPushProvider.getDeliveryType()).thenReturn("some type");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());

        // Init to verify token does not clear if the delivery type is the same
        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, runtimeConfig,
                privacyManager, pushProvidersSupplier, mockAirshipChannel, mockAnalytics, mockPermissionManager,
                mockDispatcher, mockNotificationManager, activityMonitor);
        pushManager.init();
        assertEquals("token", pushManager.getPushToken());

        // Change the delivery type, should clear the token on init
        when(mockPushProvider.getDeliveryType()).thenReturn("some other type");
        pushManager = new PushManager(TestApplication.getApplication(), preferenceDataStore, runtimeConfig,
                privacyManager, pushProvidersSupplier, mockAirshipChannel, mockAnalytics, mockPermissionManager,
                mockDispatcher, mockNotificationManager, activityMonitor);
        pushManager.init();
        assertNull(pushManager.getPushToken());
    }


    /**
     * Test on registering for a push token.
     */
    @Test
    public void testPushRegistration() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());
        verify(mockAirshipChannel).updateRegistration();
    }

    /**
     * Test push provider unavailable exceptions keep the token.
     */
    @Test
    public void testPushProviderUnavailableException() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());

        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenThrow(new PushProvider.PushProviderUnavailableException("test"));
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());
    }

    /**
     * Test registration exceptions clear the token.
     */
    @Test
    public void tesRegistrationException() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());

        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenThrow(new PushProvider.RegistrationException("test", true));
        pushManager.performPushRegistration(true);
        assertNull(pushManager.getPushToken());
    }

    /**
     * Test OptIn is only true if push and notifications are enabled and we have a push token.
     */
    @Test
    public void testOptIn() throws PushProvider.RegistrationException {
        pushManager.init();

        // Enable and have permission
        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);
        pushManager.setUserNotificationsEnabled(true);
        privacyManager.enable(PrivacyManager.Feature.PUSH);

        // Still needs a token
        assertFalse(pushManager.isOptIn());

        // Register for a token
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);

        assertTrue(pushManager.isOptIn());

        // Disable notifications
        pushManager.setUserNotificationsEnabled(false);
        assertFalse(pushManager.isOptIn());

        pushManager.setUserNotificationsEnabled(true);

        // Disable push privacy manager
        privacyManager.disable(PrivacyManager.Feature.PUSH);
        assertFalse(pushManager.isOptIn());

        privacyManager.enable(PrivacyManager.Feature.PUSH);

        // Revoke permission
        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(false);
        assertFalse(pushManager.isOptIn());
    }

    @Test
    public void testGetPushProviderType() {
        pushManager.init();

        // FCM
        when(mockPushProvider.getDeliveryType()).thenReturn(PushProvider.FCM_DELIVERY_TYPE);
        assertEquals(PushProviderType.FCM, pushManager.getPushProviderType());

        // ADM
        when(mockPushProvider.getDeliveryType()).thenReturn(PushProvider.ADM_DELIVERY_TYPE);
        assertEquals(PushProviderType.ADM, pushManager.getPushProviderType());

        // HMS
        when(mockPushProvider.getDeliveryType()).thenReturn(PushProvider.HMS_DELIVERY_TYPE);
        assertEquals(PushProviderType.HMS, pushManager.getPushProviderType());
    }

    @Test
    public void testGetPushProviderTypeNoProvider() {
        pushManager.init();
        when(mockPushProviders.getBestProvider(anyInt())).thenReturn(null);

        assertEquals(PushProviderType.NONE, pushManager.getPushProviderType());
    }

    /**
     * Test Airship notification action button groups are available
     */
    @Test
    public void testUrbanAirshipNotificationActionButtonGroups() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();
        assertTrue(keys.size() > 0);

        for (String key : keys) {
            assertNotNull("Missing notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test trying to add a notification action button group with the reserved prefix
     */
    @Test
    public void testAddingNotificationActionButtonGroupWithReservedPrefix() {
        pushManager.addNotificationActionButtonGroup("ua_my_test_id", NotificationActionButtonGroup.newBuilder().build());
        assertNull("Should not be able to add groups with prefix ua_", pushManager.getNotificationActionGroup("ua_my_test_id"));
    }

    /**
     * Test trying to remove a notification action button group with the reserved prefix
     */
    @Test
    public void testRemovingNotificationActionButtonGroupWithReservedPrefix() {
        Set<String> keys = ActionButtonGroupsParser.fromXml(RuntimeEnvironment.application, R.xml.ua_notification_buttons).keySet();

        for (String key : keys) {
            pushManager.removeNotificationActionButtonGroup(key);
            assertNotNull("Should not be able to remove notification button group with ID: " + key, pushManager.getNotificationActionGroup(key));
        }
    }

    /**
     * Test channel registration extender when push is opted in.
     */
    @Test
    public void testChannelRegistrationExtenderOptedIn() throws PushProvider.RegistrationException {
        ArgumentCaptor<AirshipChannel.Extender.Blocking> argument = ArgumentCaptor.forClass(AirshipChannel.Extender.Blocking.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.Extender.Blocking extender = argument.getValue();
        assertNotNull(extender);

        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        pushManager.setUserNotificationsEnabled(true);
        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setBackgroundEnabled(true)
                .setOptIn(true)
                .setPushAddress("token")
                .build();

        assertEquals(expected, payload);
    }

    /**
     * Test channel registration extender when push is opted out.
     */
    @Test
    public void testChannelRegistrationExtenderOptedOut() {
        ArgumentCaptor<AirshipChannel.Extender.Blocking> argument = ArgumentCaptor.forClass(AirshipChannel.Extender.Blocking.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.Extender.Blocking extender = argument.getValue();
        assertNotNull(extender);

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setBackgroundEnabled(false)
                .setOptIn(false)
                .build();

        assertEquals(expected, payload);
    }

    @Test
    public void testDeliveryTypeAndroidPlatform() throws PushProvider.RegistrationException {
        ArgumentCaptor<AirshipChannel.Extender.Blocking> argument = ArgumentCaptor.forClass(AirshipChannel.Extender.Blocking.class);
        pushManager.init();
        verify(mockAirshipChannel).addChannelRegistrationPayloadExtender(argument.capture());

        AirshipChannel.Extender.Blocking extender = argument.getValue();
        assertNotNull(extender);

        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getPlatform()).thenReturn(UAirship.ANDROID_PLATFORM);
        when(mockPushProvider.getDeliveryType()).thenReturn(PushProvider.FCM_DELIVERY_TYPE);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");

        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder();

        ChannelRegistrationPayload payload = extender.extend(builder).build();

        ChannelRegistrationPayload expected = new ChannelRegistrationPayload.Builder()
                .setPushAddress("token")
                .setDeliveryType(PushProvider.FCM_DELIVERY_TYPE)
                .setBackgroundEnabled(true)
                .build();

        assertEquals(expected, payload);
    }

    @Test
    public void testAnalyticHeaders() {
        ArgumentCaptor<Analytics.AnalyticsHeaderDelegate> captor = ArgumentCaptor.forClass(Analytics.AnalyticsHeaderDelegate.class);
        pushManager.init();
        verify(mockAnalytics).addHeaderDelegate(captor.capture());

        Analytics.AnalyticsHeaderDelegate delegate = captor.getValue();
        assertNotNull(delegate);

        Map<String, String> expectedHeaders = new HashMap<>();
        expectedHeaders.put("X-UA-Channel-Opted-In", "false");
        expectedHeaders.put("X-UA-Channel-Background-Enabled", "false");

        Map<String, String> headers = delegate.onCreateAnalyticsHeaders();
        assertEquals(expectedHeaders, headers);
    }

    @Test
    public void testOnPushReceived() {
        Bundle bundle = new Bundle();
        PushMessage message = new PushMessage(bundle);

        PushListener internalPushListener = mock(PushListener.class);
        PushListener pushListener = mock(PushListener.class);
        pushManager.addInternalPushListener(internalPushListener);
        pushManager.addPushListener(pushListener);

        pushManager.onPushReceived(message, true);
        pushManager.onPushReceived(message, false);

        verify(pushListener).onPushReceived(message, true);
        verify(internalPushListener).onPushReceived(message, true);

        verify(pushListener).onPushReceived(message, false);
        verify(internalPushListener).onPushReceived(message, false);
    }

    @Test
    public void testOnPushReceivedInternal() {
        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.REMOTE_DATA_UPDATE_KEY, "true");
        PushMessage message = new PushMessage(bundle);

        PushListener internalPushListener = mock(PushListener.class);
        PushListener pushListener = mock(PushListener.class);
        pushManager.addInternalPushListener(internalPushListener);
        pushManager.addPushListener(pushListener);

        pushManager.onPushReceived(message, false);
        verify(internalPushListener).onPushReceived(message, false);
        verifyNoInteractions(pushListener);
    }

    @Test
    public void testOnNotificationPosted() {
        Bundle bundle = new Bundle();
        bundle.putString(PushMessage.REMOTE_DATA_UPDATE_KEY, "true");
        final PushMessage message = new PushMessage(bundle);

        NotificationListener notificationListener = mock(NotificationListener.class);
        pushManager.setNotificationListener(notificationListener);

        pushManager.onNotificationPosted(message, 100, "neat");
        verify(notificationListener).onNotificationPosted(ArgumentMatchers.argThat(argument -> argument.getMessage().equals(message) && argument.getNotificationId() == 100 && argument.getNotificationTag().equals("neat")));
    }

    @Test
    public void testOnTokenChange() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());
        verify(mockAirshipChannel).updateRegistration();

        clearInvocations(mockDispatcher);

        pushManager.onTokenChanged(mockPushProvider.getClass(), "some-other-token");
        assertNull(pushManager.getPushToken());
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION)));
    }

    @Test
    public void testOnTokenChangeSameToken() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());
        verify(mockAirshipChannel).updateRegistration();

        clearInvocations(mockDispatcher);

        pushManager.onTokenChanged(mockPushProvider.getClass(), "token");
        assertEquals("token", pushManager.getPushToken());
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION)));
    }

    @Test
    public void testOnTokenChangeLegacy() throws PushProvider.RegistrationException {
        pushManager.init();
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);
        assertEquals("token", pushManager.getPushToken());
        verify(mockAirshipChannel).updateRegistration();

        clearInvocations(mockDispatcher);

        pushManager.onTokenChanged(null, null);
        assertEquals("token", pushManager.getPushToken());
        verify(mockDispatcher).dispatch(Mockito.argThat(jobInfo -> jobInfo.getAction().equals(PushManager.ACTION_UPDATE_PUSH_REGISTRATION)));
    }

    @Test
    public void testPermissionEnabler() {
        ArgumentCaptor<Consumer> captor = ArgumentCaptor.forClass(Consumer.class);
        pushManager.init();
        verify(mockPermissionManager).addAirshipEnabler(captor.capture());

        Consumer<Permission> consumer = (Consumer<Permission>) captor.getValue();
        assertNotNull(consumer);

        privacyManager.disable(PrivacyManager.Feature.PUSH);
        pushManager.setUserNotificationsEnabled(false);

        consumer.accept(Permission.DISPLAY_NOTIFICATIONS);

        assertTrue(privacyManager.isEnabled(PrivacyManager.Feature.PUSH));
        assertTrue(pushManager.getUserNotificationsEnabled());
        verify(mockAirshipChannel).updateRegistration();
    }

    @Test
    public void testForegroundChecksPermission() {
        pushManager.init();
        pushManager.onAirshipReady();
        this.notificationStatus = PermissionStatus.NOT_DETERMINED;
        pushManager.setUserNotificationsEnabled(true);
        clearInvocations(mockPermissionManager);

        activityMonitor.foreground();
        verify(mockPermissionManager).checkPermissionStatus(eq(Permission.DISPLAY_NOTIFICATIONS), any());
    }

    @Test
    public void testEnableNotificationsChecksPermission() {
        activityMonitor.foreground();
        clearInvocations(mockPermissionManager);

        this.notificationStatus = PermissionStatus.NOT_DETERMINED;
        pushManager.setUserNotificationsEnabled(true);

        verify(mockPermissionManager).checkPermissionStatus(eq(Permission.DISPLAY_NOTIFICATIONS), any());
    }

    @Test
    public void testEnableUserNotifications() {
        TestConsumer<Boolean> consumer = new TestConsumer<>();

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> callback = invocation.getArgument(3);
            callback.accept(PermissionRequestResult.granted());
            return null;
        }).when(mockPermissionManager).requestPermission(
                eq(Permission.DISPLAY_NOTIFICATIONS),
                eq(false),
                eq(PermissionPromptFallback.None.INSTANCE),
                any()
        );

        pushManager.enableUserNotifications(consumer);

        assertTrue(consumer.lastResult);
        assertTrue(pushManager.getUserNotificationsEnabled());
    }

    @Test
    public void testEnableUserNotificationsDenied() {
        TestConsumer<Boolean> consumer = new TestConsumer<>();

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> callback = invocation.getArgument(3);
            callback.accept(PermissionRequestResult.denied(true));
            return null;
        }).when(mockPermissionManager).requestPermission(
                eq(Permission.DISPLAY_NOTIFICATIONS),
                eq(false),
                eq(PermissionPromptFallback.None.INSTANCE),
                any()
        );

        pushManager.enableUserNotifications(consumer);

        assertFalse(consumer.lastResult);
        assertTrue(pushManager.getUserNotificationsEnabled());
    }

    @Test
    public void testEnableUserNotificationsFallback() {
        TestConsumer<Boolean> consumer = new TestConsumer<>();

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> callback = invocation.getArgument(3);
            callback.accept(PermissionRequestResult.granted());
            return null;
        }).when(mockPermissionManager).requestPermission(
                eq(Permission.DISPLAY_NOTIFICATIONS),
                eq(false),
                eq(PermissionPromptFallback.SystemSettings.INSTANCE),
                any()
        );

        pushManager.enableUserNotifications(PermissionPromptFallback.SystemSettings.INSTANCE, consumer);

        assertTrue(consumer.lastResult);
        assertTrue(pushManager.getUserNotificationsEnabled());
    }

    @Test
    public void testPrivacyManagerEnablesNotifications() {
        pushManager.onAirshipReady();

        this.notificationStatus = PermissionStatus.NOT_DETERMINED;

        pushManager.init();
        privacyManager.disable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        pushManager.setUserNotificationsEnabled(true);
        clearInvocations(mockPermissionManager);

        privacyManager.enable(PrivacyManager.Feature.PUSH);
        verify(mockPermissionManager).checkPermissionStatus(eq(Permission.DISPLAY_NOTIFICATIONS), any());
    }

    @Test
    public void testPermissionStatusChangesUpdatesChannelRegistration() {
        ArgumentCaptor<OnPermissionStatusChangedListener> captor = ArgumentCaptor.forClass(OnPermissionStatusChangedListener.class);
        pushManager.init();
        verify(mockPermissionManager).addOnPermissionStatusChangedListener(captor.capture());

        captor.getValue().onPermissionStatusChanged(Permission.DISPLAY_NOTIFICATIONS, PermissionStatus.DENIED);
        captor.getValue().onPermissionStatusChanged(Permission.DISPLAY_NOTIFICATIONS, PermissionStatus.GRANTED);
        captor.getValue().onPermissionStatusChanged(Permission.DISPLAY_NOTIFICATIONS, PermissionStatus.NOT_DETERMINED);

        captor.getValue().onPermissionStatusChanged(Permission.LOCATION, PermissionStatus.GRANTED);

        verify(mockAirshipChannel, times(3)).updateRegistration();
    }

    @Test
    public void testRequestPermissionWhenEnabled() {
        this.notificationStatus = PermissionStatus.DENIED;

        pushManager.init();
        pushManager.onAirshipReady();

        privacyManager.enable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        pushManager.setUserNotificationsEnabled(true);
        clearInvocations(mockPermissionManager);

        pushManager.setUserNotificationsEnabled(true);
        pushManager.setUserNotificationsEnabled(false);
        pushManager.setUserNotificationsEnabled(true);
        verify(mockPermissionManager, times(1)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());
    }

    @Test
    public void testUpdateRegistrationWhenDisabled() {
        this.notificationStatus = PermissionStatus.DENIED;
        pushManager.setUserNotificationsEnabled(true);
        clearInvocations(mockAirshipChannel);

        pushManager.setUserNotificationsEnabled(false);
        verify(mockAirshipChannel).updateRegistration();
    }

    @Test
    public void testUpdateRegistrationAfterPrompt() {
        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> statusConsumer = invocation.getArgument(1);
            statusConsumer.accept(PermissionRequestResult.granted());
            return null;
        }).when(mockPermissionManager).requestPermission(any(), any());

        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        clearInvocations(mockAirshipChannel);

        pushManager.setUserNotificationsEnabled(true);
        verify(mockAirshipChannel).updateRegistration();
    }

    @Test
    public void testCheckPermissionAirshipReady() {
        this.notificationStatus = PermissionStatus.DENIED;
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        pushManager.setUserNotificationsEnabled(true);
        clearInvocations(mockPermissionManager);

        verify(mockPermissionManager, times(0)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());

        pushManager.onAirshipReady();

        verify(mockPermissionManager, times(1)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());
    }

    @Test
    public void testPromptNotificationPermissionOncePerEnable() {
        this.notificationStatus = PermissionStatus.DENIED;
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        pushManager.onAirshipReady();

        verify(mockPermissionManager, times(0)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());

        pushManager.setUserNotificationsEnabled(true);

        verify(mockPermissionManager, times(1)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());

        activityMonitor.background();
        activityMonitor.foreground();

        verify(mockPermissionManager, times(1)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());

        pushManager.setUserNotificationsEnabled(false);
        pushManager.setUserNotificationsEnabled(true);

        verify(mockPermissionManager, times(2)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), anyBoolean(), any(), any());
    }

    @Test
    public void testPromptNotificationPermissionDisabled() {
        this.runtimeConfig.setConfigOptions(AirshipConfigOptions.newBuilder()
                                                                .setAppKey("appKey")
                                                                .setAppSecret("appSecret")
                                                                .setIsPromptForPermissionOnUserNotificationsEnabled(false)
                                                                .build());

        this.notificationStatus = PermissionStatus.DENIED;
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        activityMonitor.foreground();
        pushManager.onAirshipReady();
        pushManager.setUserNotificationsEnabled(true);
        verify(mockPermissionManager, times(0)).requestPermission(eq(Permission.DISPLAY_NOTIFICATIONS), any());
    }

    @Test
    public void testPushStatus() throws PushProvider.RegistrationException {
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        pushManager.onAirshipReady();
        pushManager.setUserNotificationsEnabled(true);

        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);
        when(mockPushProvider.isAvailable(any(Context.class))).thenReturn(true);
        when(mockPushProvider.getRegistrationToken(any(Context.class))).thenReturn("token");
        pushManager.performPushRegistration(true);

        assertEquals(
                new PushNotificationStatus(true, true, true, true),
                pushManager.getPushNotificationStatus()
        );
    }

    @Test
    public void testPushStatusNoToken() {
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        pushManager.onAirshipReady();
        pushManager.setUserNotificationsEnabled(true);
        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);

        assertEquals(
                new PushNotificationStatus(true, true, true, false),
                pushManager.getPushNotificationStatus()
        );
    }

    public void testPushStatusChangedListenerEnableNotifications() {
        PushNotificationStatusListener listener = mock(PushNotificationStatusListener.class);

        pushManager.setUserNotificationsEnabled(false);
        pushManager.addNotificationStatusListener(listener);

        TestConsumer<Boolean> consumer = new TestConsumer<>();

        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> callback = invocation.getArgument(3);
            callback.accept(PermissionRequestResult.granted());
            return null;
        }).when(mockPermissionManager).requestPermission(
                eq(Permission.DISPLAY_NOTIFICATIONS),
                eq(false),
                eq(PermissionPromptFallback.None.INSTANCE),
                any()
        );

        pushManager.enableUserNotifications(consumer);

        assertTrue(consumer.lastResult);
        assertTrue(pushManager.getUserNotificationsEnabled());

        pushManager.enableUserNotifications(TestCase::assertTrue);

        verify(listener, times(1)).onChange(any());
    }

    @Test
    public void testPushStatusChanges() {
        pushManager.init();
        privacyManager.enable(PrivacyManager.Feature.PUSH);
        pushManager.onAirshipReady();
        pushManager.setUserNotificationsEnabled(true);
        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);

        PushNotificationStatus expected = new PushNotificationStatus(true, true, true, false);
        assertEquals(expected, pushManager.getPushNotificationStatus());

        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(false);
        assertEquals(
                new PushNotificationStatus(true, false, true, false),
                pushManager.getPushNotificationStatus()
        );

        when(mockNotificationManager.areNotificationsEnabled()).thenReturn(true);
        pushManager.setUserNotificationsEnabled(false);
        assertEquals(
                new PushNotificationStatus(false, true, true, false),
                pushManager.getPushNotificationStatus()
        );

        pushManager.setUserNotificationsEnabled(true);
        privacyManager.disable(PrivacyManager.Feature.PUSH);
        assertEquals(
                new PushNotificationStatus(true, true, false, false),
                pushManager.getPushNotificationStatus()
        );
    }

    private static class TestConsumer<T> implements Consumer<T> {
        T lastResult;
        List<T> results = new ArrayList<>();

        @Override
        public void accept(T result) {
            this.lastResult = result;
            this.results.add(result);
        }

    }
}
