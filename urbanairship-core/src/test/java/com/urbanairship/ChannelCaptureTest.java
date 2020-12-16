/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.urbanairship.channel.AirshipChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressLint("NewApi")
public class ChannelCaptureTest extends BaseTestCase {

    private ChannelCapture capture;
    private AirshipChannel mockChannel;
    private AirshipConfigOptions configOptions;
    private ClipboardManager clipboardManager;
    private PreferenceDataStore dataStore;
    private TestActivityMonitor activityMonitor;

    @Before
    public void setup() {
        clipboardManager = (ClipboardManager) ApplicationProvider.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);

        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        mockChannel = mock(AirshipChannel.class);
        activityMonitor = new TestActivityMonitor();
        dataStore = TestApplication.getApplication().preferenceDataStore;

        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        clearClipboard();
    }

    @After
    public void takeDown() {
        capture.setEnabled(false);
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
        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        when(mockChannel.getId()).thenReturn("channel ID");

        knock(6);

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            assertEquals("", clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
        } else {
            assert true;
        }
    }

    /**
     * Test enabling the channel capture through AirshipConfigOptions.
     */
    @Test
    public void testChannelCaptureEnabled() {
        // Enable the channel capture
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(true)
                .build();

        // Reinitialize it
        capture.tearDown();
        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        when(mockChannel.getId()).thenReturn("channel ID");

        knock(6);

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertEquals("ua:" + mockChannel.getId(), clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
    }

    /**
     * Test the channel capture with Channel ID null.
     */
    @Test
    public void testChannelCaptureEnabledChannelNull() {
        // Enable the channel capture
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(true)
                .build();

        // Reinitialize it
        capture.tearDown();
        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        when(mockChannel.getId()).thenReturn(null);

        knock(6);

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertEquals("ua:", clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
    }

    /**
     * Test the channel capture on a single knock.
     */
    @Test
    public void testChannelCaptureSingleForeground() {
        // Enable the channel capture
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(true)
                .build();

        // Reinitialize it
        capture.tearDown();
        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        when(mockChannel.getId()).thenReturn("Channel ID");

        knock(1);

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            assertEquals("", clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
        } else {
            assert true;
        }
    }

    /**
     * Test channel capture requires 6 knocks each time.
     */
    @Test
    public void testChannelCaptureRequires6Knocks() {
        // Enable the channel capture
        configOptions = new AirshipConfigOptions.Builder()
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .setChannelCaptureEnabled(true)
                .build();

        // Reinitialize it
        capture.tearDown();
        capture = new ChannelCapture(ApplicationProvider.getApplicationContext(), configOptions, mockChannel, dataStore, activityMonitor);

        capture.init();

        when(mockChannel.getId()).thenReturn("channel ID");

        knock(6);

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertEquals("ua:" + mockChannel.getId(), clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));

        clearClipboard();
        clipData = clipboardManager.getPrimaryClip();

        knock(1);

        clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            assertEquals("", clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
        } else {
            assert true;
        }

        knock(5);

        clipData = clipboardManager.getPrimaryClip();
        assertEquals("ua:" + mockChannel.getId(), clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));

    }

    /**
     * Test enabling the channel capture at runtime
     */
    @Test
    public void testEnable() {
        capture.setEnabled(true);
        assertTrue(capture.isEnabled());
    }

    /**
     * Test disabling the channel capture at runtime
     */
    @Test
    public void testDisable() {
        capture.setEnabled(true);
        capture.setEnabled(false);
        assertFalse(capture.isEnabled());
    }

    /**
     * Send one or more knocks
     */
    public void knock(int repeat) {
        for (int i = 0; i < repeat; i++) {
            activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        }
    }

    public void clearClipboard() {
        ClipData clipData = ClipData.newPlainText("", "");
        clipboardManager.setPrimaryClip(clipData);
    }
}
