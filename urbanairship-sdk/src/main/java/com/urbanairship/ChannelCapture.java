/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Base64;

import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * ChannelCapture checks the device clipboard for a String that is prefixed by
 * {@link #generateToken()} on app foreground and posts a notification
 * that allows the user to copy the Channel or optionally open a url with the channel as
 * an argument.
 */
public class ChannelCapture extends AirshipComponent {

    static final String CHANNEL_CAPTURE_ENABLED_KEY = "com.urbanairship.CHANNEL_CAPTURE_ENABLED";

    static final String CHANNEL = "channel";
    static final String URL = "url";

    private final static String CHANNEL_PLACEHOLDER = "CHANNEL";
    private final static String GO_URL = "https://go.urbanairship.com/";

    private final Context context;
    private final AirshipConfigOptions configOptions;
    private final PushManager pushManager;
    private ClipboardManager clipboardManager;
    private final ActivityMonitor.Listener listener;
    private final ActivityMonitor activityMonitor;
    private final PreferenceDataStore preferenceDataStore;

    Executor executor = Executors.newSingleThreadExecutor();

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param pushManager The push manager instance.
     * @param activityMonitor The activity monitor instance.
     */
    ChannelCapture(Context context, AirshipConfigOptions configOptions, PushManager pushManager, PreferenceDataStore preferenceDataStore,
                   ActivityMonitor activityMonitor) {
        this.context = context.getApplicationContext();
        this.configOptions = configOptions;
        this.pushManager = pushManager;
        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long time) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        checkClipboard();
                    }
                });
            }
        };
        this.preferenceDataStore = preferenceDataStore;
        this.activityMonitor = activityMonitor;
    }


    @Override
    protected void init() {
        // ClipboardManager must be prepared on a thread with a prepared looper
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                activityMonitor.addListener(listener);
            }
        });
    }

    /**
     * Enable channel capture for the specified period of time.
     *
     * @param duration The duration of time.
     * @param unit The time unit.
     */
    public void enable(long duration, TimeUnit unit) {
        long milliDuration = unit.toMillis(duration);
        preferenceDataStore.put(CHANNEL_CAPTURE_ENABLED_KEY, System.currentTimeMillis() + milliDuration);
    }

    /**
     * Disable channel capture.
     */
    public void disable() {
        preferenceDataStore.put(CHANNEL_CAPTURE_ENABLED_KEY, 0);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(listener);
    }

    /**
     * Checks the clipboard for the token and starts the activity if the token is
     * available.
     */
    private void checkClipboard() {
        String channel = pushManager.getChannelId();

        // Only perform checks if notifications are enabled for the app.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            if (!this.configOptions.channelCaptureEnabled) {
                return;
            }

            if (UAirship.shared().getPushManager().isPushEnabled()
                    && this.preferenceDataStore.getLong(CHANNEL_CAPTURE_ENABLED_KEY, 0) < System.currentTimeMillis()) {
                this.preferenceDataStore.put(CHANNEL_CAPTURE_ENABLED_KEY, 0);
                return;
            }

            if (UAStringUtil.isEmpty(channel)) {
                return;
            }

            // Clipboard is null on a few VodaPhone devices
            if (clipboardManager == null) {
                return;
            }
        }

        String clipboardText = null;
        try {
            if (!clipboardManager.hasPrimaryClip()) {
                return;
            }

            ClipData primaryClip = clipboardManager.getPrimaryClip();

            if (primaryClip != null && primaryClip.getItemCount() > 0) {

                for (int i = 0; i < primaryClip.getItemCount(); i++) {
                    ClipData.Item item = primaryClip.getItemAt(i);
                    CharSequence text = item.getText();
                    if (text != null) {
                        clipboardText = text.toString();
                    }
                }
            }
        } catch (SecurityException e) {
            Logger.debug("Unable to read clipboard: " + e.getMessage());
            return;
        }

        String decodedClipboardString = base64Decode(clipboardText);
        String superSecretCode = generateToken();
        if (UAStringUtil.isEmpty(decodedClipboardString) || !decodedClipboardString.startsWith(superSecretCode)) {
            return;
        }

        // Perform the magic
        String url = null;
        if (decodedClipboardString.length() > superSecretCode.length()) {
            url = decodedClipboardString.replace(superSecretCode, GO_URL)
                                        .replace(CHANNEL_PLACEHOLDER, channel)
                                        .trim();
        }

        try {
            // Clear the clipboard
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
        } catch (SecurityException e) {
            Logger.debug("Unable to clear clipboard: " + e.getMessage());
        }

        startChannelCaptureActivity(channel, url);
    }

    /**
     * Create the intent that launches the {@link ChannelCaptureActivity}
     *
     * @param channel The channel string.
     * @param url The channel url.
     */
    private void startChannelCaptureActivity(String channel, String url) {
        Intent intent = new Intent(context, ChannelCaptureActivity.class);
        intent.putExtra(CHANNEL, channel);
        intent.putExtra(URL, url);
        context.startActivity(intent);
    }

    /**
     * Base64 decodes a string.
     *
     * @param encoded The base64 encoded string.
     * @return The decoded string or null if it failed to be decoded.
     */
    private String base64Decode(String encoded) {
        if (UAStringUtil.isEmpty(encoded)) {
            return null;
        }

        // Decode it
        try {
            byte[] data = Base64.decode(encoded, Base64.DEFAULT);
            return new String(data, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            Logger.verbose("ClipBoardMagic - Unsupported encoding.");
            return null;
        } catch (IllegalArgumentException e) {
            Logger.verbose("ClipBoardMagic - Failed to decode string.");
            return null;
        }
    }

    /**
     * Generates the expected clipboard token.
     *
     * @return The generated clipboard token.
     */
    @NonNull
    private String generateToken() {
        byte[] appKeyBytes = configOptions.getAppKey().getBytes();
        byte[] appSecretBytes = configOptions.getAppSecret().getBytes();

        StringBuilder code = new StringBuilder();

        for (int i = 0; i < appKeyBytes.length; i++) {
            byte b = (byte) (appKeyBytes[i] ^ appSecretBytes[i % appSecretBytes.length]);
            code.append(String.format("%02x", b));
        }

        return code.toString();
    }
}
