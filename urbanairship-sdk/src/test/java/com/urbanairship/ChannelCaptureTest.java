/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Base64;

import com.urbanairship.push.PushManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowPendingIntent;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@SuppressLint("NewApi")
public class ChannelCaptureTest extends BaseTestCase {

    private ChannelCapture capture;
    private PushManager mockPushManager;
    private AirshipConfigOptions configOptions;
    private ClipboardManager clipboardManager;
    private NotificationManagerCompat mockNotificationManager;
    private PreferenceDataStore dataStore;
    private TestActivityMonitor activityMonitor;

    @Before
    public void setup() {
        clipboardManager = (ClipboardManager) RuntimeEnvironment.application.getSystemService(Context.CLIPBOARD_SERVICE);
        mockNotificationManager = mock(NotificationManagerCompat.class);

        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(true)
                .build();

        mockPushManager = mock(PushManager.class);
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();
        dataStore = TestApplication.getApplication().preferenceDataStore;

        capture = new ChannelCapture(RuntimeEnvironment.application, configOptions, mockPushManager, mockNotificationManager, dataStore, activityMonitor);

        // Replace the executor so it runs everything right away
        capture.executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };

        capture.init();
    }

    @After
    public void takeDown() {
        activityMonitor.unregister();
    }

    /**
     * Test app foreground when the clipboard contains the clipboard capture token
     * but the channel does not exist.
     */
    @Test
    public void testForegroundClipboardWithTokenNoChannel() {
        when(mockPushManager.getChannelId()).thenReturn(null);
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken(null)));

        activityMonitor.startActivity();

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

        activityMonitor.startActivity();

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

        activityMonitor.startActivity();

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

        activityMonitor.startActivity();

        // Capture the posted notification
        ArgumentCaptor<Notification> argumentCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(mockNotificationManager).notify(eq(3000), argumentCaptor.capture());

        // Verify the notification
        Notification notification = argumentCaptor.getValue();
        assertEquals(".TestApplication", shadowOf(notification).getContentTitle());
        assertEquals("channel ID", shadowOf(notification).getContentText());
        assertEquals(2, notification.actions.length);

        // Notification pending intent
        ShadowPendingIntent shadowPendingContentIntent = shadowOf(notification.contentIntent);
        Intent contentIntent = shadowPendingContentIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", contentIntent.getAction());
        assertEquals(3000, contentIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"open_external_url_action\":\"https:\\/\\/go.urbanairship.com\\/\\/oh_hi\"}", contentIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Copy action
        Notification.Action copyAction = notification.actions[0];
        assertEquals("Copy", copyAction.title);

        // Copy action pending intent
        ShadowPendingIntent shadowCopyPendingIntent = shadowOf(copyAction.actionIntent);
        Intent copyIntent = shadowCopyPendingIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", copyIntent.getAction());
        assertEquals(3000, copyIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"},\"toast_action\":\"Channel copied to clipboard!\"}", copyIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Save action
        Notification.Action saveAction =  notification.actions[1];
        assertEquals("Save", saveAction.title);

        // Save action pending intent
        ShadowPendingIntent shadowOpenPendingIntent = shadowOf(saveAction.actionIntent);
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

        activityMonitor.startActivity();

        // Capture the posted notification
        ArgumentCaptor<Notification> argumentCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(mockNotificationManager).notify(eq(3000), argumentCaptor.capture());

        // Verify the notification
        Notification notification = argumentCaptor.getValue();
        assertEquals(".TestApplication", shadowOf(notification).getContentTitle());
        assertEquals("channel ID", shadowOf(notification).getContentText());
        assertEquals(1, notification.actions.length);

        // Notification pending intent - should default to the copy intent
        ShadowPendingIntent shadowPendingContentIntent = shadowOf(notification.contentIntent);
        Intent contentIntent = shadowPendingContentIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", contentIntent.getAction());
        assertEquals(3000, contentIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"},\"toast_action\":\"Channel copied to clipboard!\"}", contentIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));

        // Copy action
        Notification.Action copyAction = notification.actions[0];
        assertEquals("Copy", copyAction.title);

        // Copy action pending intent
        ShadowPendingIntent shadowCopyPendingIntent = shadowOf(copyAction.actionIntent);
        Intent copyIntent = shadowCopyPendingIntent.getSavedIntent();
        assertEquals("com.urbanairship.ACTION_CHANNEL_CAPTURE", copyIntent.getAction());
        assertEquals(3000, copyIntent.getIntExtra("com.urbanairship.EXTRA_NOTIFICATION_ID", -1));
        assertEquals("{\"clipboard_action\":{\"text\":\"channel ID\",\"label\":\"Urban Airship Channel\"},\"toast_action\":\"Channel copied to clipboard!\"}", copyIntent.getStringExtra("com.urbanairship.EXTRA_ACTIONS"));
    }

    /**
     * Test disabling the channel capture through AirshipConfigOptions.
     */
    @Test
    public void testChannelCaptureDisabled() {
        // Disable the channel capture
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(false)
                .build();

        // Reinitialize it
        capture.tearDown();
        capture = new ChannelCapture(RuntimeEnvironment.application, configOptions, mockPushManager, mockNotificationManager, dataStore, activityMonitor);

        // Replace the executor so it runs everything right away
        capture.executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };
        capture.init();

        // Set up a valid token
        when(mockPushManager.getChannelId()).thenReturn("channel ID");
        clipboardManager.setPrimaryClip(ClipData.newPlainText("Channel", generateToken(null)));

        activityMonitor.startActivity();

        // Verify we did not post a notification
        verifyZeroInteractions(mockNotificationManager);
    }

    @Test
    public void testEnable() {
        capture.enable(100, TimeUnit.SECONDS);
        assertNotEquals(dataStore.getLong(ChannelCapture.CHANNEL_CAPTURE_ENABLED_KEY, 0), 0);
    }

    @Test
    public void testDisable() {
        capture.enable(100, TimeUnit.SECONDS);
        capture.disable();
        assertEquals(dataStore.getLong(ChannelCapture.CHANNEL_CAPTURE_ENABLED_KEY, -1), 0);
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
