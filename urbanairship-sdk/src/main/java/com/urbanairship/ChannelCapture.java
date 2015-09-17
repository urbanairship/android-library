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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Base64;

import com.urbanairship.actions.ClipboardAction;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * ChannelCapture checks the device clipboard for a String that is prefixed by
 * {@link #generateToken()} on app foreground and posts a notification
 * that allows the user to copy the Channel or optionally open a url with the channel as
 * an argument.
 */
@TargetApi(Build.VERSION_CODES.FROYO)
class ChannelCapture extends BaseManager {

    /**
     * Broadcast action when the clipboard notification is tapped.
     */
    static final String ACTION_CHANNEL_CAPTURE = "com.urbanairship.ACTION_CHANNEL_CAPTURE";

    /**
     * Notification ID extra.
     */
    static final String EXTRA_NOTIFICATION_ID = "com.urbanairship.EXTRA_NOTIFICATION_ID";

    /**
     * Action payload extra.
     */
    static final String EXTRA_ACTIONS = "com.urbanairship.EXTRA_ACTIONS";

    private final static int NOTIFICATION_ID = 3000;
    private final static String CHANNEL_PLACEHOLDER = "CHANNEL";
    private final static String GO_URL = "https://go.urbanairship.com/";

    private final Context context;
    private final AirshipConfigOptions configOptions;
    private final PushManager pushManager;
    private final NotificationManagerCompat notificationManager;
    private Clipboard clipboard;
    private final BroadcastReceiver broadcastReceiver;

    Executor executor = Executors.newSingleThreadExecutor();

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param pushManager The push manager instance.
     */
    ChannelCapture(Context context, AirshipConfigOptions configOptions, PushManager pushManager) {
       this(context, configOptions, pushManager, NotificationManagerCompat.from(context));
    }

    ChannelCapture(Context context, AirshipConfigOptions configOptions, PushManager pushManager,
                   NotificationManagerCompat notificationManager) {

        this.context = context.getApplicationContext();
        this.configOptions = configOptions;
        this.pushManager = pushManager;
        this.notificationManager = notificationManager;

        this.broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        checkClipboard();
                    }
                });
            }
        };
    }


    @Override
    protected void init() {
        if (!this.configOptions.channelCaptureEnabled) {
            return;
        }

        // Magic must be prepared on a thread with a prepared looper
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                // Clipboard must be initialized on a thread with a prepared looper
                clipboard = Build.VERSION.SDK_INT >= 11 ? new ClipboardHoneyComb() : new ClipboardFroyo();

                IntentFilter filter = new IntentFilter();
                filter.addAction(Analytics.ACTION_APP_FOREGROUND);
                LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
                broadcastManager.registerReceiver(broadcastReceiver, filter);
            }
        });
    }

    @Override
    protected void tearDown() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(broadcastReceiver);
    }

    /**
     * Checks the clipboard for the token and posts the notification if
     * the token is available.
     */
    private void checkClipboard() {
        String channel = pushManager.getChannelId();
        if (UAStringUtil.isEmpty(channel)) {
            return;
        }

        String clipboardText = clipboard.getText();
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

        clipboard.clear();
        displayNotification(channel, url);
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
     * Displays a notification with the channel ID.
     *
     * @param channel The channel ID.
     * @param url The test channel URL.
     */
    private void displayNotification(String channel, String url) {
        PendingIntent copyClipboardPendingIntent = createCopyChannelPendingIntent(channel);
        PendingIntent openUrlPendingIntent = url == null ? null : createOpenUrlPendingIntent(url);

        String appName = context.getPackageManager().getApplicationLabel(context.getApplicationInfo()).toString();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context)
                .setAutoCancel(true)
                .setLocalOnly(true)
                .setContentTitle(appName)
                .setContentText(channel)
                .setSmallIcon(R.drawable.ic_urbanairship_notification)
                .setColor(context.getResources().getColor(R.color.urban_airship_blue))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setTicker(context.getString(R.string.ua_channel_notification_ticker))
                .setContentIntent(openUrlPendingIntent == null ? copyClipboardPendingIntent : openUrlPendingIntent)
                .addAction(new NotificationCompat.Action(R.drawable.ic_notification_button_copy,
                        context.getString(R.string.ua_notification_button_copy),
                        copyClipboardPendingIntent));

        if (openUrlPendingIntent != null) {
            builder.addAction(new NotificationCompat.Action(R.drawable.ic_notification_button_open_browser,
                    context.getString(R.string.ua_notification_button_save),
                    openUrlPendingIntent));
        }

        // Post the notification
        notificationManager.notify(NOTIFICATION_ID, builder.build());
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

    /**
     * Creates the copy channel notification action's pending intent.
     *
     * @param channel The channel to copy to the clipboard.
     * @return A pending intent.
     */
    @NonNull
    private PendingIntent createCopyChannelPendingIntent(@NonNull String channel) {
        Map<String, String> actionValue = new HashMap<>();
        actionValue.put("text", channel);
        actionValue.put("label", "Urban Airship Channel");

        Map<String, Object> actionPayload = new HashMap<>();
        actionPayload.put(ClipboardAction.DEFAULT_REGISTRY_NAME, actionValue);

        Intent intent = new Intent(context, CoreReceiver.class)
                .setAction(ACTION_CHANNEL_CAPTURE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                .putExtra(EXTRA_ACTIONS, JsonValue.wrap(actionPayload, null).toString());

        return PendingIntent.getBroadcast(context, NOTIFICATION_ID, intent, 0);
    }

    /**
     * Creates the open url notification action's pending intent.
     *
     * @param url The url to open.
     * @return A pending intent.
     */
    @NonNull
    private PendingIntent createOpenUrlPendingIntent(@NonNull String url) {
        Map<String, Object> actionPayload = new HashMap<>();
        actionPayload.put("open_external_url_action", url);

        Intent intent = new Intent(context, CoreActivity.class)
                .setAction(ACTION_CHANNEL_CAPTURE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                .putExtra(EXTRA_ACTIONS, JsonValue.wrap(actionPayload, null).toString());

        return PendingIntent.getActivity(context, NOTIFICATION_ID, intent, 0);
    }

    /**
     * Common clipboard interface.
     */
    private interface Clipboard {

        /**
         * Gets the text from the clipboard.
         *
         * @return The clipboard text or null if the clipboard is empty.
         */
        String getText();

        /**
         * Clears the clipboard's text.
         */
        void clear();
    }

    /**
     * Clipboard interface for HoneyComb and newer devices.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private class ClipboardHoneyComb implements Clipboard {

        private final ClipboardManager clipboardManager;

        ClipboardHoneyComb() {
            clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        }

        @Override
        public String getText() {
            ClipData primaryClip = clipboardManager.getPrimaryClip();

            if (primaryClip != null && primaryClip.getItemCount() > 0) {

                for (int i = 0; i < primaryClip.getItemCount(); i++) {
                    ClipData.Item item = primaryClip.getItemAt(i);
                    CharSequence text = item.getText();
                    if (text != null) {
                        return text.toString();
                    }
                }
            }

            return null;
        }

        @Override
        public void clear() {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("", ""));
        }
    }

    /**
     * Clipboard interface for Froyo and newer devices.
     */
    private class ClipboardFroyo implements Clipboard {

        private final android.text.ClipboardManager clipboardManager;

        ClipboardFroyo() {
            clipboardManager = (android.text.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        }

        @Override
        public String getText() {
            return String.valueOf(clipboardManager.getText());
        }

        @Override
        public void clear() {
            clipboardManager.setText("");
        }
    }

}
