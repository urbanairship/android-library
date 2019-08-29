/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestPushProvider;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.iam.LegacyInAppMessageManager;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationChannelCompat;
import com.urbanairship.push.notifications.NotificationChannelRegistry;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.push.notifications.NotificationResult;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.Collections;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class IncomingPushRunnableTest extends BaseTestCase {

    public static final int TEST_NOTIFICATION_ID = 123;
    public static final String TEST_NOTIFICATION_CHANNEL_ID = "Test notification channel";

    private Bundle pushBundle;
    private PushMessage message;

    private PushListener pushListener;
    private NotificationListener notificationListener;

    private PushManager pushManager;
    private NotificationManagerCompat notificationManager;
    private Analytics analytics;
    private LegacyInAppMessageManager legacyInAppMessageManager;
    private NotificationChannelRegistry mockChannelRegistry;

    private TestNotificationProvider notificationProvider;

    private IncomingPushRunnable pushRunnable;
    private IncomingPushRunnable displayRunnable;
    private TestPushProvider testPushProvider;

    private JobDispatcher jobDispatcher;

    @Before
    public void setup() {
        pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        pushBundle.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "testSendID");
        pushBundle.putString(PushMessage.EXTRA_NOTIFICATION_TAG, "testNotificationTag");
        message = new PushMessage(pushBundle);

        pushManager = mock(PushManager.class);
        pushListener = mock(PushListener.class);
        notificationListener = mock(NotificationListener.class);
        testPushProvider = new TestPushProvider();

        when(pushManager.getPushProvider()).thenReturn(testPushProvider);
        when(pushManager.getNotificationListener()).thenReturn(notificationListener);
        when(pushManager.getPushListeners()).thenReturn(Collections.singletonList(pushListener));

        notificationManager = mock(NotificationManagerCompat.class);

        when(pushManager.isPushAvailable()).thenReturn(true);

        mockChannelRegistry = mock(NotificationChannelRegistry.class);
        when(pushManager.getNotificationChannelRegistry()).thenReturn(mockChannelRegistry);

        notificationProvider = new TestNotificationProvider();

        when(pushManager.getNotificationProvider()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return notificationProvider;
            }
        });

        analytics = mock(Analytics.class);
        legacyInAppMessageManager = mock(LegacyInAppMessageManager.class);

        TestApplication.getApplication().setLegacyInAppMessageManager(legacyInAppMessageManager);
        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setAnalytics(analytics);

        jobDispatcher = mock(JobDispatcher.class);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .build();

        displayRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .setProcessed(true)
                .build();
    }

    /**
     * Test displaying a notification from a push message.
     */
    @Test
    public void testDisplayNotification() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider.notification = createNotification();
        notificationProvider.tag = "testNotificationTag";

        pushRunnable.run();

        verify(notificationManager).notify("testNotificationTag", TEST_NOTIFICATION_ID, notificationProvider.notification);
        verify(analytics).addEvent(any(PushArrivedEvent.class));

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notificationProvider.notification.contentIntent);
        assertTrue("The pending intent is an activity intent.", shadowPendingIntent.isActivityIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_RESPONSE);
        assertBundlesEquals("The push message bundles should match.", pushBundle, intent.getExtras().getBundle(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE));
        assertEquals("One category should exist.", 1, intent.getCategories().size());

        verify(pushListener).onPushReceived(message, true);
        verify(notificationListener).onNotificationPosted(any(NotificationInfo.class));
    }

    /**
     * Test receiving a push from an invalid provider.
     */
    @Test
    public void testInvalidPushProvider() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass("wrong  class")
                .setMessage(new PushMessage(pushBundle))
                .setLongRunning(true)
                .setNotificationManager(notificationManager)
                .build();

        pushRunnable.run();

        verifyZeroInteractions(notificationManager);
    }

    /**
     * Test user notifications disabled still notifies user of a background notification.
     */
    @Test
    public void testUserNotificationsDisabled() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(false);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        verify(pushListener).onPushReceived(message, false);
    }

    /**
     * Test handling a background push.
     */
    @Test
    public void testBackgroundPush() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        verify(pushListener).onPushReceived(message, false);
    }

    /**
     * Test handling an exceptions from the notification provider.
     */
    @Test
    public void testNotificationProviderException() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Set a notification factory that throws an exception
        notificationProvider = new TestNotificationProvider() {
            @NonNull
            @Override
            public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
                throw new RuntimeException("Unable to create and display notification.");
            }
        };

        pushRunnable.run();

        verify(notificationManager, Mockito.never()).notify(Mockito.anyString(), Mockito.anyInt(), any(Notification.class));
        verify(jobDispatcher, Mockito.never()).dispatch(any(JobInfo.class));
    }

    @Test
    public void testNotificationProviderSuccess() {
        notificationProvider.notification = createNotification();

        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        verify(notificationManager).notify(null, TEST_NOTIFICATION_ID, notificationProvider.notification);
        verify(jobDispatcher, Mockito.never()).dispatch(any(JobInfo.class));
    }

    /**
     * Test that when the provider returns a cancel status, no notification is posted and no jobs are scheduled
     */
    @Test
    public void testNotificationProviderResultCancel() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider = new TestNotificationProvider() {
            @NonNull
            @Override
            public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
                return NotificationResult.cancel();
            }
        };

        displayRunnable.run();

        verify(notificationManager, Mockito.never()).notify(Mockito.anyString(), Mockito.anyInt(), any(Notification.class));
        verify(jobDispatcher, Mockito.never()).dispatch(any(JobInfo.class));
    }

    /**
     * Test that when the factory returns a retry status, no notification is posted and a retry job is scheduled
     */
    @Test
    public void testNotificationFactoryResultRetry() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider = new TestNotificationProvider() {
            @NonNull
            @Override
            public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
                return NotificationResult.retry();
            }
        };

        displayRunnable.run();

        verify(notificationManager, Mockito.never()).notify(Mockito.anyString(), Mockito.anyInt(), any(Notification.class));
        verify(jobDispatcher).dispatch(any(JobInfo.class));
    }

    /**
     * Test that when the factory returns a successful result, a notification is posted and no jobs are scheduled.
     */
    @Test
    public void testNotificationFactoryResultOK() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider.notification = createNotification();

        displayRunnable.run();

        verify(notificationManager).notify(null, TEST_NOTIFICATION_ID, notificationProvider.notification);
        verify(jobDispatcher, Mockito.never()).dispatch(any(JobInfo.class));
    }

    /**
     * Test notification content intent
     */
    @Test
    public void testNotificationContentIntent() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notificationProvider.notification = createNotification();
        notificationProvider.tag = "cool-tag";
        notificationProvider.notification.contentIntent = pendingIntent;

        pushRunnable.run();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notificationProvider.notification.contentIntent);
        assertTrue("The pending intent is an activity intent.", shadowPendingIntent.isActivityIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_RESPONSE);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification content intent is not null.", pendingIntent);
        assertSame("The notification content intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT));
        assertSame( "cool-tag", intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_TAG));
        assertSame( TEST_NOTIFICATION_ID, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_ID));

    }

    /**
     * Test notification delete intent
     */
    @Test
    public void testNotificationDeleteIntent() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notificationProvider.notification = createNotification();
        notificationProvider.notification.deleteIntent = pendingIntent;

        pushRunnable.run();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notificationProvider.notification.deleteIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_DISMISSED);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification delete intent is not null.", pendingIntent);
        assertSame("The notification delete intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT));
    }

    /**
     * Test that when a push is delivered pre-Oreo the notification settings are drawn
     * from our notification channel compat layer.
     */
    @Test
    @Config(sdk = 25)
    public void testDeliverPushPreOreo() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Create a channel and set some non-default values
        NotificationChannelCompat channelCompat = new NotificationChannelCompat(TEST_NOTIFICATION_CHANNEL_ID, "Test Notification Channel", NotificationManager.IMPORTANCE_HIGH);
        channelCompat.setSound(Uri.parse("cool://sound"));
        channelCompat.enableVibration(true);
        channelCompat.enableLights(true);
        channelCompat.setLightColor(123);

        when(mockChannelRegistry.getNotificationChannelSync(TEST_NOTIFICATION_CHANNEL_ID)).thenReturn(channelCompat);
        notificationProvider.notification = createNotification();

        pushRunnable.run();

        Notification notification = notificationProvider.notification;

        assertEquals(notification.sound, channelCompat.getSound());
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channelCompat.getImportance());
        assertEquals(notification.defaults & Notification.DEFAULT_VIBRATE, Notification.DEFAULT_VIBRATE);
        assertEquals(notification.ledARGB, channelCompat.getLightColor());
    }

    /**
     * Test the legacy in-app message manager is notified of the push.
     */
    @Test
    public void testNotifyLegacyIamManager() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        PushMessage push = new PushMessage(pushBundle);
        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(push)
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .build();

        pushRunnable.run();

        verify(legacyInAppMessageManager).onPushReceived(push);
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(RuntimeEnvironment.application, "some-channel")
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .build();
    }

    public static class TestNotificationProvider implements NotificationProvider {

        public Notification notification;
        public String tag;

        @NonNull
        @Override
        public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
            return NotificationArguments.newBuilder(message)
                                        .setNotificationChannelId(TEST_NOTIFICATION_CHANNEL_ID)
                                        .setNotificationId(tag, TEST_NOTIFICATION_ID)
                                        .build();
        }

        @NonNull
        @Override
        public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
            if (notification != null) {
                return NotificationResult.notification(notification);
            } else {
                return NotificationResult.cancel();
            }
        }

        @Override
        public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) {

        }

    }

}
