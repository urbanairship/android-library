/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;

import com.urbanairship.push.PushManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

@SuppressLint("NewApi")
public class ChannelCaptureTest extends BaseTestCase {

    private ChannelCapture capture;
    private PushManager mockPushManager;
    private AirshipConfigOptions configOptions;
    private ClipboardManager clipboardManager;
    private PreferenceDataStore dataStore;
    private TestActivityMonitor activityMonitor;

    @Before
    public void setup() {
        clipboardManager = (ClipboardManager) RuntimeEnvironment.application.getSystemService(Context.CLIPBOARD_SERVICE);

        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        mockPushManager = mock(PushManager.class);
        activityMonitor = new TestActivityMonitor();
        activityMonitor.register();
        dataStore = TestApplication.getApplication().preferenceDataStore;

        capture = new ChannelCapture(RuntimeEnvironment.application, configOptions, mockPushManager, dataStore, activityMonitor);

        // Replace the executor so it runs everything right away
        capture.executor = new Executor() {
            @Override
            public void execute(Runnable runnable) {
                runnable.run();
            }
        };
        capture.enable(600, TimeUnit.SECONDS);
        capture.init();
    }

    @After
    public void takeDown() {
        activityMonitor.unregister();
        capture.disable();
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

        assertEquals(shadowOf(RuntimeEnvironment.application).getNextStartedActivity(), null);
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
        assertEquals(shadowOf(RuntimeEnvironment.application).getNextStartedActivity(), null);
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
        assertEquals(shadowOf(RuntimeEnvironment.application).getNextStartedActivity(), null);
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

        Intent intent = shadowOf(RuntimeEnvironment.application).getNextStartedActivity();

        // Verify the intent
        assertNotNull(intent);
        assertEquals(intent.getComponent().getClassName(), "com.urbanairship.ChannelCaptureActivity");
        assertEquals("channel ID", intent.getExtras().getString(ChannelCapture.CHANNEL));
        assertEquals("https://go.urbanairship.com//oh_hi", intent.getExtras().getString(ChannelCapture.URL));
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

        Intent intent = shadowOf(RuntimeEnvironment.application).getNextStartedActivity();

        // Verify the intent
        assertNotNull(intent);
        assertEquals(intent.getComponent().getClassName(), "com.urbanairship.ChannelCaptureActivity");
        assertEquals("channel ID", intent.getExtras().getString(ChannelCapture.CHANNEL));
        assertEquals(null, intent.getExtras().getString(ChannelCapture.URL));
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
        capture = new ChannelCapture(RuntimeEnvironment.application, configOptions, mockPushManager, dataStore, activityMonitor);

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

        // Verify we did not post a notification TODO
        assertEquals(shadowOf(RuntimeEnvironment.application).getNextStartedActivity(), null);
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
