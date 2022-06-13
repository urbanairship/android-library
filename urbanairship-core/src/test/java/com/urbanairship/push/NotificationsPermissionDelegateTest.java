/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.Activity;

import com.urbanairship.BaseTestCase;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.permission.PermissionRequestResult;
import com.urbanairship.permission.PermissionStatus;
import com.urbanairship.push.notifications.NotificationChannelRegistry;

import org.junit.Test;

import java.util.UUID;

import androidx.core.util.Consumer;
import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotificationsPermissionDelegateTest extends BaseTestCase {

    private final String defaultChannelId = UUID.randomUUID().toString();
    private final PreferenceDataStore dataStore = PreferenceDataStore.inMemoryStore(ApplicationProvider.getApplicationContext());
    private final NotificationChannelRegistry channelRegistry = mock(NotificationChannelRegistry.class);
    private final AirshipNotificationManager notificationManager = mock(AirshipNotificationManager.class);
    private final TestActivityMonitor activityMonitor = new TestActivityMonitor();
    private final NotificationsPermissionDelegate.PermissionRequestDelegate permissionRequestDelegate = mock(NotificationsPermissionDelegate.PermissionRequestDelegate.class);

    private final NotificationsPermissionDelegate delegate = new NotificationsPermissionDelegate(defaultChannelId, dataStore, notificationManager, channelRegistry, activityMonitor, permissionRequestDelegate);
    private final TestConsumer<PermissionStatus> testCheckConsumer = new TestConsumer<>();
    private final TestConsumer<PermissionRequestResult> testRequestConsumer = new TestConsumer<>();

    @Test
    public void testCheckStatusUnsupportedPrompt() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.NOT_SUPPORTED);

        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.DENIED, testCheckConsumer.result);
    }

    @Test
    public void testRequestUnsupportedPrompt() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.NOT_SUPPORTED);

        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result.getPermissionStatus());

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.DENIED, testRequestConsumer.result.getPermissionStatus());
        assertTrue(testRequestConsumer.result.isSilentlyDenied());

    }

    @Test
    public void testCheckCompatPrompt() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.COMPAT);

        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.NOT_DETERMINED, testCheckConsumer.result);
    }

    @Test
    public void testCheckCompatPromptAfterRequest() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.COMPAT);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), status -> {
        });
        activityMonitor.resumeActivity(new Activity());

        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.DENIED, testCheckConsumer.result);
    }

    @Test
    public void testRequestCompatPrompt() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.COMPAT);

        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result.getPermissionStatus());

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        testCheckConsumer.result = null;
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);

        // Waits for activity resume to check
        assertNull(testCheckConsumer.result);
        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        activityMonitor.resumeActivity(new Activity());
        assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result.getPermissionStatus());
        assertFalse(testRequestConsumer.result.isSilentlyDenied());
    }

    @Test
    public void testRequestCompatPromptChannelsCreated() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.COMPAT);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        when(notificationManager.areChannelsCreated()).thenReturn(true);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);

        assertEquals(PermissionStatus.DENIED, testRequestConsumer.result.getPermissionStatus());
        assertTrue(testRequestConsumer.result.isSilentlyDenied());
    }

    @Test
    public void testRequestCompatPromptCreateChannel() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.COMPAT);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        when(notificationManager.areChannelsCreated()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), status -> {});

        verify(channelRegistry).getNotificationChannel(defaultChannelId);
    }

    @Test
    public void testCheckSupportedPrompt() {
        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.SUPPORTED);

        when(notificationManager.areNotificationsEnabled()).thenReturn(true);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.GRANTED, testCheckConsumer.result);

        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.NOT_DETERMINED, testCheckConsumer.result);
    }

    @Test
    public void testCheckSupportedPromptAfterRequest() {
        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> resultConsumer = invocation.getArgument(2);
            resultConsumer.accept(PermissionRequestResult.denied(true));
            return null;
        }).when(permissionRequestDelegate).requestPermissions(any(), eq("android.permission.POST_NOTIFICATIONS"), any());

        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.SUPPORTED);
        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), status -> {});
        activityMonitor.resumeActivity(new Activity());

        delegate.checkPermissionStatus(ApplicationProvider.getApplicationContext(), testCheckConsumer);
        assertEquals(PermissionStatus.DENIED, testCheckConsumer.result);
    }

    @Test
    public void testRequestSupportedPromptGranted() {
        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> resultConsumer = invocation.getArgument(2);
            resultConsumer.accept(PermissionRequestResult.granted());
            return null;
        }).when(permissionRequestDelegate).requestPermissions(any(), eq("android.permission.POST_NOTIFICATIONS"), any());

        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.SUPPORTED);
        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.GRANTED, testRequestConsumer.result.getPermissionStatus());
    }

    @Test
    public void testRequestSupportedPromptDenied() {
        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> resultConsumer = invocation.getArgument(2);
            resultConsumer.accept(PermissionRequestResult.denied(false));
            return null;
        }).when(permissionRequestDelegate).requestPermissions(any(), eq("android.permission.POST_NOTIFICATIONS"), any());

        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.SUPPORTED);
        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.DENIED, testRequestConsumer.result.getPermissionStatus());
        assertFalse(testRequestConsumer.result.isSilentlyDenied());
    }

    @Test
    public void testRequestSupportedPromptSilentlyDenied() {
        doAnswer(invocation -> {
            Consumer<PermissionRequestResult> resultConsumer = invocation.getArgument(2);
            resultConsumer.accept(PermissionRequestResult.denied(true));
            return null;
        }).when(permissionRequestDelegate).requestPermissions(any(), eq("android.permission.POST_NOTIFICATIONS"), any());

        when(notificationManager.getPromptSupport()).thenReturn(AirshipNotificationManager.PromptSupport.SUPPORTED);
        when(notificationManager.areNotificationsEnabled()).thenReturn(false);
        delegate.requestPermission(ApplicationProvider.getApplicationContext(), testRequestConsumer);
        assertEquals(PermissionStatus.DENIED, testRequestConsumer.result.getPermissionStatus());
        assertTrue(testRequestConsumer.result.isSilentlyDenied());
    }

    private static class TestConsumer<T> implements androidx.core.util.Consumer<T> {
        T result;
        @Override
        public void accept(T result) {
            this.result = result;
        }

    }
}
