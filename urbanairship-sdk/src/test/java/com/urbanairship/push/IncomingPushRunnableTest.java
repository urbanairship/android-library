/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.TestPushProvider;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.notifications.NotificationFactory;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;


public class IncomingPushRunnableTest extends BaseTestCase {

    public static final int TEST_NOTIFICATION_ID = 123;

    private Bundle pushBundle;

    private PushManager pushManager;
    private NotificationManagerCompat notificationManager;
    private Analytics analytics;

    private Notification notification;
    private NotificationFactory notificationFactory;

    private IncomingPushRunnable pushRunnable;
    private TestPushProvider testPushProvider;

    @Before
    public void setup() {
        pushBundle = new Bundle();
        pushBundle.putString(PushMessage.EXTRA_ALERT, "Test Push Alert!");
        pushBundle.putString(PushMessage.EXTRA_PUSH_ID, "testPushID");
        pushBundle.putString(PushMessage.EXTRA_SEND_ID, "testSendID");
        pushBundle.putString(PushMessage.EXTRA_NOTIFICATION_TAG, "testNotificationTag");


        pushManager = mock(PushManager.class);
        testPushProvider = new TestPushProvider();
        when(pushManager.getPushProvider()).thenReturn(testPushProvider);

        notificationManager = mock(NotificationManagerCompat.class);

        when(pushManager.isPushAvailable()).thenReturn(true);

        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .build();

        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(@NonNull PushMessage pushMessage, int notificationId) {
                return notification;
            }

            @Override
            public int getNextId(@NonNull PushMessage pushMessage) {
                return TEST_NOTIFICATION_ID;
            }
        };

        when(pushManager.getNotificationFactory()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return notificationFactory;
            }
        });


        analytics = mock(Analytics.class);

        TestApplication.getApplication().setPushManager(pushManager);
        TestApplication.getApplication().setAnalytics(analytics);

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .build();
    }

    /**
     * Test displaying a notification from a push message.
     */
    @Test
    public void testDisplayNotification() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        verify(notificationManager).notify("testNotificationTag", TEST_NOTIFICATION_ID, notification);
        verify(analytics).addEvent(any(PushArrivedEvent.class));

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.contentIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_OPENED_PROXY);
        assertBundlesEquals("The push message bundles should match.", pushBundle, intent.getExtras().getBundle(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE));
        assertEquals("One category should exist.", 1, intent.getCategories().size());
    }

    /**
     * Test receiving a push from an invalid provider.
     */
    @Test
    public void testInvalidPushProvider() {
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
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(false);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);
        PushMessage push = PushMessage.fromIntent(i);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", "testPushID", push.getCanonicalPushId());
        assertEquals("No notification ID should be present", i.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1), -1);
    }

    /**
     * Test handling a background push.
     */
    @Test
    public void testBackgroundPush() {
        notification = null;
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushRunnable.run();

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);
        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);

        PushMessage push = PushMessage.fromIntent(i);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", "testPushID", push.getCanonicalPushId());
        assertEquals("No notification ID should be present", -1, i.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1));
    }

    /**
     * Test handling an exceptions from the notification factory.
     */
    @Test
    public void testNotificationFactoryException() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Set a notification factory that throws an exception
        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(@NonNull PushMessage pushMessage, int notificationId) {
                throw new RuntimeException("Unable to create and display notification.");
            }

            @Override
            public int getNextId(@NonNull PushMessage pushMessage) {
                return 0;
            }
        };

        pushRunnable.run();

        verify(notificationManager, Mockito.never()).notify(Mockito.anyInt(), any(Notification.class));
    }

    /**
     * Test notification content intent
     */
    @Test
    public void testNotificationContentIntent() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        pushRunnable.run();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.contentIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_OPENED_PROXY);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification content intent is not null.", pendingIntent);
        assertSame("The notification content intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT));
    }

    /**
     * Test notification delete intent
     */
    @Test
    public void testNotificationDeleteIntent() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setDeleteIntent(pendingIntent)
                .build();

        pushRunnable.run();

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.deleteIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY);
        assertEquals("One category should exist.", 1, intent.getCategories().size());
        assertNotNull("The notification delete intent is not null.", pendingIntent);
        assertSame("The notification delete intent matches.", pendingIntent, intent.getExtras().get(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT));
    }

    /**
     * Test when sound is disabled the flag for DEFAULT_SOUND is removed and the notification sound
     * is set to null.
     */
    @Test
    public void testDeliverPushSoundDisabled() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Disable sound
        when(pushManager.isSoundEnabled()).thenReturn(false);

        notification.sound = Uri.parse("some://sound");
        notification.defaults = NotificationCompat.DEFAULT_ALL;

        pushRunnable.run();

        assertNull("The notification sound should be null.", notification.sound);
        assertEquals("The notification defaults should not include DEFAULT_SOUND.",
                notification.defaults & NotificationCompat.DEFAULT_SOUND, 0);
    }

    /**
     * Test when sound is disabled the flag for DEFAULT_VIBRATE is removed and the notification vibrate
     * is set to null.
     */
    @Test
    public void testDeliverPushVibrateDisabled() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        // Disable vibrate
        when(pushManager.isVibrateEnabled()).thenReturn(false);

        notification.defaults = NotificationCompat.DEFAULT_ALL;
        notification.vibrate = new long[] { 0L, 1L, 200L };

        pushRunnable.run();

        assertNull("The notification sound should be null.", notification.vibrate);
        assertEquals("The notification defaults should not include DEFAULT_VIBRATE.",
                notification.defaults & NotificationCompat.DEFAULT_VIBRATE, 0);
    }

    /**
     * Test delivering a push with an in-app message sets the pending notification.
     */
    @Test
    public void testDeliverPushInAppMessage() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        pushBundle.putString(PushMessage.EXTRA_IN_APP_MESSAGE, new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .create()
                .toJsonValue()
                .toString());

        pushRunnable = new IncomingPushRunnable.Builder(TestApplication.getApplication())
                .setProviderClass(testPushProvider.getClass().toString())
                .setMessage(new PushMessage(pushBundle))
                .setNotificationManager(notificationManager)
                .setLongRunning(true)
                .build();

        pushRunnable.run();

        assertEquals(new PushMessage(pushBundle).getInAppMessage(), UAirship.shared().getInAppMessageManager().getPendingMessage());
    }

    /**
     * Test the notification defaults in quiet time.
     */
    @Test
    public void testInQuietTime() {
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.isOptIn()).thenReturn(true);
        when(pushManager.isUniqueCanonicalId("testPushID")).thenReturn(true);

        when(pushManager.isVibrateEnabled()).thenReturn(true);
        when(pushManager.isSoundEnabled()).thenReturn(true);
        when(pushManager.isInQuietTime()).thenReturn(true);

        pushRunnable.run();

        assertNull("The notification sound should be null.", notification.sound);
        assertEquals("The notification defaults should not include vibrate or sound.", 0, notification.defaults);
    }



}
