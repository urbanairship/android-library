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

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;

import com.urbanairship.analytics.Analytics;
import com.urbanairship.push.PushManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowNotification;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@SuppressLint("NewApi")
public class ChannelCaptureTest extends BaseTestCase {

    private ChannelCapture capture;
    private PushManager mockPushManager;
    private AirshipConfigOptions configOptions;
    private ClipboardManager clipboardManager;
    private NotificationManagerCompat mockNotificationManager;

    @Before
    public void setup() {
        clipboardManager = (ClipboardManager) RuntimeEnvironment.application.getSystemService(Context.CLIPBOARD_SERVICE);
        mockNotificationManager = mock(NotificationManagerCompat.class);

        configOptions = new AirshipConfigOptions();
        configOptions.developmentAppKey = "app key";
        configOptions.developmentAppSecret = "app secret";
        configOptions.inProduction = false;

        mockPushManager = mock(PushManager.class);

        capture = new ChannelCapture(RuntimeEnvironment.application, configOptions, mockPushManager, mockNotificationManager);

        // Replace the executor so it runs everything right away
        capture.executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        capture.init();
    }

    /**
     * Test app foreground when the clipboard contains the clipboard capture token
     * but the channel does not exist.
     */
    @Test
    public void testForegroundClipboardWithTokenNoChannel() {
        when(mockPushManager.getChannelId()).thenReturn(null);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken(null)));


        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Verify we did not post a notification
        verifyZeroInteractions(mockNotificationManager);
    }

    /**
     * Test app foreground when the clipboard is empty.
     */
    @Test
    public void testForegroundEmptyClipboard() {
        when(mockPushManager.getChannelId()).thenReturn("channel ID");
        clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));

        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Verify we did not post a notification
        verifyZeroInteractions(mockNotificationManager);
    }

    /**
     * Test app foreground when the clipboard has a String that differs from
     * the expected channel capture token.
     */
    @Test
    public void testForegroundClipboardWithoutToken() {
        when(mockPushManager.getChannelId()).thenReturn("channel ID");
        clipboardManager.setPrimaryClip(ClipData.newPlainText("WHAT!", "OK!"));

        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Verify we did not post a notification
        verifyZeroInteractions(mockNotificationManager);
    }

    /**
     * Test app foreground when the clipboard contains the expected token and
     * a channel is created.
     */
    @Test
    public void testForegroundClipboardWithToken() {
        when(mockPushManager.getChannelId()).thenReturn("channel ID");
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken("/oh_hi")));

        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Capture the posted notification
        ArgumentCaptor<Notification> argumentCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(mockNotificationManager).notify(eq(3000), argumentCaptor.capture());

        // Verify the notification
        ShadowNotification notification = Shadows.shadowOf(argumentCaptor.getValue());
        assertEquals(".TestApplication", notification.getContentTitle());
        assertEquals("channel ID", notification.getContentText());
        assertEquals(2, notification.getActions().size());

        // Notification pending intent
        ShadowPendingIntent shadowPendingContentIntent = Shadows.shadowOf(notification.getRealNotification().contentIntent);
        Intent contentIntent = shadowPendingContentIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", contentIntent.getAction());
        assertEquals(3000, contentIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"open_external_url_action\":\"https:\\/\\/go.urbanairship.com\\/\\/oh_hi\"}", contentIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Copy action
        Notification.Action copyAction = notification.getActions().get(0);
        assertEquals("Copy", copyAction.title);

        // Copy action pending intent
        ShadowPendingIntent shadowCopyPendingIntent = Shadows.shadowOf(copyAction.actionIntent);
        Intent copyIntent = shadowCopyPendingIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", copyIntent.getAction());
        assertEquals(3000, copyIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"}}", copyIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Save action
        Notification.Action saveAction = notification.getActions().get(1);
        assertEquals("Save", saveAction.title);

        // Save action pending intent
        ShadowPendingIntent shadowOpenPendingIntent = Shadows.shadowOf(saveAction.actionIntent);
        Intent openIntent = shadowOpenPendingIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", copyIntent.getAction());
        assertEquals(3000, copyIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"open_external_url_action\":\"https:\\/\\/go.urbanairship.com\\/\\/oh_hi\"}", openIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));
    }

    /**
     * Test app foreground when the clipboard contains the expected token without a URL.
     */
    @Test
    public void testForegroundClipboardWithTokenNoUrl() {
        when(mockPushManager.getChannelId()).thenReturn("channel ID");

        // Set the token without a Url
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken(null)));

        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Capture the posted notification
        ArgumentCaptor<Notification> argumentCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(mockNotificationManager).notify(eq(3000), argumentCaptor.capture());

        // Verify the notification
        ShadowNotification notification = Shadows.shadowOf(argumentCaptor.getValue());
        assertEquals(".TestApplication", notification.getContentTitle());
        assertEquals("channel ID", notification.getContentText());
        assertEquals(1, notification.getActions().size());

        // Notification pending intent - should default to the copy intent
        ShadowPendingIntent shadowPendingContentIntent = Shadows.shadowOf(notification.getRealNotification().contentIntent);
        Intent contentIntent = shadowPendingContentIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", contentIntent.getAction());
        assertEquals(3000, contentIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"}}", contentIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Copy action
        Notification.Action copyAction = notification.getActions().get(0);
        assertEquals("Copy", copyAction.title);

        // Copy action pending intent
        ShadowPendingIntent shadowCopyPendingIntent = Shadows.shadowOf(copyAction.actionIntent);
        Intent copyIntent = shadowCopyPendingIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", copyIntent.getAction());
        assertEquals(3000, copyIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"}}", copyIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));
    }

    /**
     * Test disabling the channel capture through AirshipConfigOptions.
     */
    @Test
    public void testChannelCaptureDisabled() {
        // Disable the channel capture
        configOptions.channelCaptureEnabled = false;

        // Reinitialize it
        capture.tearDown();
        capture.init();

        // Set up a valid token
        when(mockPushManager.getChannelId()).thenReturn("channel ID");
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken(null)));

        // Send the foreground broadcast
        LocalBroadcastManager.getInstance(TestApplication.getApplication())
                             .sendBroadcast(new Intent(Analytics.ACTION_APP_FOREGROUND));

        // Verify we did not post a notification
        verifyZeroInteractions(mockNotificationManager);
    }

    /**
     * Helper method to generate the channel capture clipboard token.
     *
     * @param urlPath Optional url path.
     * @return The clipboard token.
     */
    private String generateToken(String urlPath) {
        byte[] appKeyBytes = configOptions.getAppKey().getBytes();
        byte[] appSecretBytes = configOptions.getAppSecret().getBytes();

        StringBuilder token = new StringBuilder();

        for (int i = 0; i < appKeyBytes.length; i++) {
            byte b = (byte) (appKeyBytes[i] ^ appSecretBytes[i % appSecretBytes.length]);
            token.append(String.format("%02x", b));
        }

        if (urlPath != null) {
            token.append(urlPath);
        }

        return Base64.encodeToString(token.toString().getBytes(), Base64.DEFAULT);
    }
}