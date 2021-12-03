/* Copyright Airship and Contributors */

package com.urbanairship.push;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestActivityMonitor;
import com.urbanairship.TestApplication;
import com.urbanairship.TestPushProvider;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.js.TestActivity;
import com.urbanairship.modules.accengage.AccengageNotificationHandler;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationChannelCompat;
import com.urbanairship.push.notifications.NotificationChannelRegistry;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.push.notifications.NotificationResult;
import com.urbanairship.util.PendingIntentCompat;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowPendingIntent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class IncomingPushRunnableTest extends BaseTestCase {

    private static final int TEST_NOTIFICATION_ID = 123;
    private static final String TEST_NOTIFICATION_CHANNEL_ID = "Test notification channel";

    private Bundle pushBundle;
    private PushMessage message;
    private PushMessage accengageMessage;

    private PushManager pushManager;
    private NotificationManagerCompat notificationManager;
    private Analytics analytics;
    private NotificationChannelRegistry mockChannelRegistry;

    private TestNotificationProvider notificationProvider;
    private TestNotificationProvider accengageNotificationProvider;

    private IncomingPushRunnable pushRunnable;
    private IncomingPushRunnable displayRunnable;
    private TestPushProvider testPushProvider;

    private JobDispatcher jobDispatcher;
    private TestActivityMonitor activityMonitor;

    @Before
    public void setup() {
        activityMonitor = new TestActivityMonitor();
        pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        pushBundle.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "testSendID");
        pushBundle.putString(PushMessage.EXTRA_NOTIFICATION_TAG, "testNotificationTag");
        message = new PushMessage(pushBundle);

        Bundle bundle = new Bundle();
        bundle.putString("a4scontent", "neat");
        bundle.putInt("a4sid", 77);
        accengageMessage = new PushMessage(bundle);

        pushManager = mock(PushManager.class);
        testPushProvider = new TestPushProvider();

        when(pushManager.getPushProvider()).thenReturn(testPushProvider);
        notificationManager = mock(NotificationManagerCompat.class);

        when(pushManager.isPushAvailable()).thenReturn(true);

        mockChannelRegistry = mock(NotificationChannelRegistry.class);
        when(pushManager.getNotificationChannelRegistry()).thenReturn(mockChannelRegistry);

        notificationProvider = new TestNotificationProvider();
        accengageNotificationProvider = new TestNotificationProvider();

        when(pushManager.getNotificationProvider()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) {
                return notificationProvider;
            }
        });

        analytics = mock(Analytics.class);

        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setAnalytics(analytics);

        jobDispatcher = mock(JobDispatcher.class);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .setActivityMonitor(activityMonitor)
                .build();

        displayRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .setProcessed(true)
                .setActivityMonitor(activityMonitor)
                .build();

        TestApplication.getApplication().setAccengageNotificationHandler(new AccengageNotificationHandler() {
            @NonNull
            @Override
            public NotificationProvider getNotificationProvider() {
                return accengageNotificationProvider;
            }
        });
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

        verify(pushManager).onPushReceived(message, true);
        verify(pushManager).onNotificationPosted(message, TEST_NOTIFICATION_ID, "testNotificationTag");
    }

    /**
     * Test ignoring push from other vendors.
     */
    @Test
    public void test() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider.notification = createNotification();
        notificationProvider.tag = "testNotificationTag";

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass("wrong  class")
                .setMessage(new PushMessage(pushBundle))
                .setLongRunning(true)
                .setNotificationManager(notificationManager)
                .build();

        pushRunnable.run();

        verifyNoInteractions(notificationManager);
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

        verifyNoInteractions(notificationManager);
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

        verify(pushManager).onPushReceived(message, false);
    }

    /**
     * Test suppress message in foreground when isForegroundDisplayable is false.
     */
    @Test
    public void testNotForegroundDisplayable() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        activityMonitor.foreground();

        notificationProvider.notification = createNotification();
        notificationProvider.tag = "testNotificationTag";

        pushBundle.putString("com.urbanairship.foreground_display", "false");
        message = new PushMessage(pushBundle);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(message)
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .setActivityMonitor(activityMonitor)
                .build();

        pushRunnable.run();

        verify(pushManager).onPushReceived(message, false);
    }

    /**
     * Test message in foreground when isForegroundDisplayable is true.
     */
    @Test
    public void testForegroundDisplayable() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        activityMonitor.foreground();

        notificationProvider.notification = createNotification();
        notificationProvider.tag = "testNotificationTag";

        pushBundle.putString("com.urbanairship.foreground_display", "true");
        message = new PushMessage(pushBundle);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(message)
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .setActivityMonitor(activityMonitor)
                .build();

        pushRunnable.run();

        verify(pushManager).onPushReceived(message, true);
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

        verify(pushManager).onPushReceived(message, false);
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

    @Test
    public void testAccengageNotificationProvider() {
        accengageNotificationProvider.notification = createNotification();

        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId(null)).thenReturn(true);


        IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(accengageMessage)
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .build();

        pushRunnable.run();

        verify(notificationManager).notify(null, TEST_NOTIFICATION_ID, accengageNotificationProvider.notification);
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

        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
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

        PendingIntent pendingIntent = PendingIntentCompat.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
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
     * Test remote data notifications
     */
    @Test
    public void testRemoteDataMessage() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Set a notification factory that throws an exception
        notificationProvider = mock(TestNotificationProvider.class);

        Bundle pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "testSendID");
        pushBundle.putString(PushMessage.REMOTE_DATA_UPDATE_KEY, "true");
        PushMessage message = new PushMessage(pushBundle);

        new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(message)
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .setJobDispatcher(jobDispatcher)
                .build()
                .run();

        verify(notificationManager, Mockito.never()).notify(Mockito.anyString(), Mockito.anyInt(), any(Notification.class));
        verify(jobDispatcher, Mockito.never()).dispatch(any(JobInfo.class));
        verifyNoInteractions(notificationProvider);
        verify(pushManager).onPushReceived(message, false);
        verify(analytics).addEvent(any(PushArrivedEvent.class));
    }

    @Test
    public void testNullNotificationChannel() {
        when(pushManager.isComponentEnabled()).thenReturn(true);
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        notificationProvider.notification = new NotificationCompat.Builder(TestApplication.getApplication())
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .build();

        notificationProvider.tag = "testNotificationTag";

        pushRunnable.run();

        verifyNoInteractions(mockChannelRegistry);
        verify(notificationManager).notify("testNotificationTag", TEST_NOTIFICATION_ID, notificationProvider.notification);
        verify(analytics).addEvent(any(PushArrivedEvent.class));

        verify(pushManager).onPushReceived(message, true);
        verify(pushManager).onNotificationPosted(message, TEST_NOTIFICATION_ID, "testNotificationTag");
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(TestApplication.getApplication(),"some-channel")
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
