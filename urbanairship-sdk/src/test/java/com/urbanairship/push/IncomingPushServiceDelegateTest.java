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

package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
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
import static org.mockito.Mockito.when;


public class IncomingPushServiceDelegateTest extends BaseTestCase {

    private Intent silentGcmIntent;
    private Intent alertingGcmIntent;

    private PushManager pushManager;
    private PushPreferences pushPreferences;
    private NotificationManagerCompat notificationManager;

    private Notification notification;
    public final int constantNotificationId = 123;
    private NotificationFactory notificationFactory;

    private IncomingPushServiceDelegate serviceDelegate;

    @Before
    public void setup() {

        alertingGcmIntent = new Intent(GcmConstants.ACTION_GCM_RECEIVE)
                .putExtra(PushMessage.EXTRA_ALERT, "Test Push Alert!")
                .putExtra(PushMessage.EXTRA_PUSH_ID, "testPushID");

        silentGcmIntent = new Intent(GcmConstants.ACTION_GCM_RECEIVE)
                .putExtra(PushMessage.EXTRA_PUSH_ID, "silentPushID");

        pushPreferences = mock(PushPreferences.class);
        pushManager = mock(PushManager.class);
        notificationManager = mock(NotificationManagerCompat.class);

        when(pushManager.isPushAvailable()).thenReturn(true);

        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .build();

        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(PushMessage pushMessage, int notificationId) {
                return notification;
            }

            @Override
            public int getNextId(PushMessage pushMessage) {
                return constantNotificationId;
            }
        };

        when(pushManager.getPreferences()).thenReturn(pushPreferences);
        when(pushManager.getNotificationFactory()).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return notificationFactory;
            }
        });


        TestApplication.getApplication().setPushManager(pushManager);

        serviceDelegate = new IncomingPushServiceDelegate(TestApplication.getApplication(),
                TestApplication.getApplication().preferenceDataStore, UAirship.shared(), notificationManager);
    }


    /**
     * Test deliver push notification.
     */
    @Test
    public void testDeliverPush() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);
        verify(notificationManager).notify(constantNotificationId, notification);

        ShadowPendingIntent shadowPendingIntent = Shadows.shadowOf(notification.contentIntent);
        assertTrue("The pending intent is broadcast intent.", shadowPendingIntent.isBroadcastIntent());

        Intent intent = shadowPendingIntent.getSavedIntent();
        assertEquals("The intent action should match.", intent.getAction(), PushManager.ACTION_NOTIFICATION_OPENED_PROXY);
        assertEquals("The push message bundles should match.", alertingGcmIntent.getExtras(), ((PushMessage)intent.getExtras().get(PushManager.EXTRA_PUSH_MESSAGE)).getPushBundle());
        assertEquals("One category should exist.", 1, intent.getCategories().size());
    }

    /**
     * Test deliver background notification.
     */
    @Test
    public void testDeliverPushUserPushDisabled() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(false);

        serviceDelegate.onHandleIntent(pushIntent);

        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);
        Bundle extras = i.getExtras();
        PushMessage push = extras.getParcelable(PushManager.EXTRA_PUSH_MESSAGE);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", "testPushID", push.getCanonicalPushId());
        assertEquals("No notification ID should be present", extras.getInt(PushManager.EXTRA_NOTIFICATION_ID, -1), -1);
    }


    /**
     * Test deliver background notification.
     */
    @Test
    public void testDeliverBackgroundPush() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, silentGcmIntent);

        ShadowApplication shadowApplication = Shadows.shadowOf(RuntimeEnvironment.application);

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(false);

        serviceDelegate.onHandleIntent(pushIntent);

        List<Intent> intents = shadowApplication.getBroadcastIntents();
        Intent i = intents.get(intents.size() - 1);
        Bundle extras = i.getExtras();
        PushMessage push = extras.getParcelable(PushManager.EXTRA_PUSH_MESSAGE);
        assertEquals("Intent action should be push received", i.getAction(), PushManager.ACTION_PUSH_RECEIVED);
        assertEquals("Push ID should equal pushMessage ID", "silentPushID", push.getCanonicalPushId());
        assertEquals("No notification ID should be present", extras.getInt(PushManager.EXTRA_NOTIFICATION_ID, -1), -1);
    }

    /**
     * Test handling an exception
     */
    @Test
    public void testDeliverPushException() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);


        // Set a notification factory that throws an exception
        notificationFactory = new NotificationFactory(TestApplication.getApplication()) {
            @Override
            public Notification createNotification(PushMessage pushMessage, int notificationId) {
                throw new RuntimeException("Unable to create and display notification.");
            }

            @Override
            public int getNextId(PushMessage pushMessage) {
                return 0;
            }
        };

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);

        verify(notificationManager, Mockito.never()).notify(Mockito.anyInt(), any(Notification.class));
    }

    /**
     * Test notification content intent
     */
    @Test
    public void testNotificationContentIntent() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);

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
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(RuntimeEnvironment.application, 1, new Intent(), 0);
        notification = new NotificationCompat.Builder(RuntimeEnvironment.application)
                .setContentTitle("Test NotificationBuilder Title")
                .setContentText("Test NotificationBuilder Text")
                .setAutoCancel(true)
                .setDeleteIntent(pendingIntent)
                .build();

        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);

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
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        // Enable push
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        // Disable sound
        when(pushManager.isSoundEnabled()).thenReturn(false);

        notification.sound = Uri.parse("some://sound");
        notification.defaults = NotificationCompat.DEFAULT_ALL;

        serviceDelegate.onHandleIntent(pushIntent);
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
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        // Enable push
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        // Disable vibrate
        when(pushManager.isVibrateEnabled()).thenReturn(false);

        notification.defaults = NotificationCompat.DEFAULT_ALL;
        notification.vibrate = new long[] { 0L, 1L, 200L };

        serviceDelegate.onHandleIntent(pushIntent);
        assertNull("The notification sound should be null.", notification.vibrate);
        assertEquals("The notification defaults should not include DEFAULT_VIBRATE.",
                notification.defaults & NotificationCompat.DEFAULT_VIBRATE, 0);
    }

    /**
     * Test delivering a push with an in-app message sets the pending notification.
     */
    @Test
    public void testDeliverPushInAppMessage() {
        alertingGcmIntent.putExtra(PushMessage.EXTRA_IN_APP_MESSAGE, new InAppMessage.Builder()
                .setAlert("oh hi")
                .setExpiry(1000l)
                .create()
                .toJsonValue()
                .toString());

        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        // Enable push
        when(pushManager.isPushEnabled()).thenReturn(true);
        when(pushManager.getUserNotificationsEnabled()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);

        assertEquals(new PushMessage(alertingGcmIntent.getExtras()).getInAppMessage(), UAirship.shared().getInAppMessageManager().getPendingMessage());
    }

    /**
     * Test the notification defaults: in quiet time.
     */
    @Test
    public void testInQuietTime() {
        Intent pushIntent = new Intent(PushService.ACTION_RECEIVE_GCM_MESSAGE)
                .putExtra(PushService.EXTRA_INTENT, alertingGcmIntent);

        when(pushManager.isVibrateEnabled()).thenReturn(true);
        when(pushManager.isSoundEnabled()).thenReturn(true);
        when(pushManager.isInQuietTime()).thenReturn(true);

        serviceDelegate.onHandleIntent(pushIntent);
        assertNull("The notification sound should be null.", notification.sound);
        assertEquals("The notification defaults should not include vibrate or sound.", 0, notification.defaults);
    }

}