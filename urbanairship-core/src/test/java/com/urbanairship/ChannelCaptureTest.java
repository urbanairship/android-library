/* Copyright Airship and Contributors */

package com.urbanairship;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.urbanairship.channel.AirshipChannel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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

        clipboardManager.clearPrimaryClip();
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

        knock();

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            assertNotEquals("ua:" + mockChannel.getId(), clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
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

        knock();

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

        knock();

        ClipData clipData = clipboardManager.getPrimaryClip();
        assertEquals("ua:", clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
    }

    /**
     * Test the channel capture on a single foreground.
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

        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());

        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null) {
            assertNotEquals("ua:" + mockChannel.getId(), clipData.getItemAt(0).coerceToText(ApplicationProvider.getApplicationContext()));
        } else {
            assert true;
        }
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
     * Just send a knock (6 foregrounds)
     */
    public void knock() {
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
        activityMonitor.foreground(Calendar.getInstance().getTimeInMillis());
    }

}
