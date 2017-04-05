/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Base64;

import com.urbanairship.actions.ClipboardAction;
import com.urbanairship.actions.ToastAction;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    ChannelCapture(Context context, AirshipConfigOptions configOptions, PushManager pushManager,
                   PreferenceDataStore preferenceDataStore, ActivityMonitor activityMonitor) {
        this(context, configOptions, pushManager, NotificationManagerCompat.from(context), preferenceDataStore, activityMonitor);
    }

    ChannelCapture(Context context, AirshipConfigOptions configOptions, PushManager pushManager,
                   NotificationManagerCompat notificationManager, PreferenceDataStore preferenceDataStore,
                   ActivityMonitor activityMonitor) {
        this.context = context.getApplicationContext();
        this.configOptions = configOptions;
        this.pushManager = pushManager;
        this.notificationManager = notificationManager;
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
     * Checks the clipboard for the token and posts the notification if
     * the token is available.
     */
    private void checkClipboard() {
        if (!this.configOptions.channelCaptureEnabled) {
            return;
        }

        if (UAirship.shared().getPushManager().isPushEnabled()
                && this.preferenceDataStore.getLong(CHANNEL_CAPTURE_ENABLED_KEY, 0) < System.currentTimeMillis()) {
            this.preferenceDataStore.put(CHANNEL_CAPTURE_ENABLED_KEY, 0);
            return;
        }

        String channel = pushManager.getChannelId();
        if (UAStringUtil.isEmpty(channel)) {
            return;
        }

        // Clipboard is null on a few VodaPhone devices
        if (clipboardManager == null) {
            return;
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
                .setSmallIcon(R.drawable.ua_ic_urbanairship_notification)
                .setColor(ContextCompat.getColor(context, R.color.urban_airship_blue))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setTicker(context.getString(R.string.ua_channel_notification_ticker))
                .setContentIntent(openUrlPendingIntent == null ? copyClipboardPendingIntent : openUrlPendingIntent)
                .addAction(new NotificationCompat.Action(R.drawable.ua_ic_notification_button_copy,
                        context.getString(R.string.ua_notification_button_copy),
                        copyClipboardPendingIntent));

        if (openUrlPendingIntent != null) {
            builder.addAction(new NotificationCompat.Action(R.drawable.ua_ic_notification_button_open_browser,
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
        actionPayload.put(ToastAction.DEFAULT_REGISTRY_NAME, context.getString(R.string.ua_channel_copy_toast));

        Intent intent = new Intent(context, CoreReceiver.class)
                .setAction(ACTION_CHANNEL_CAPTURE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
                .putExtra(EXTRA_ACTIONS, JsonValue.wrapOpt(actionPayload).toString());

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
                .putExtra(EXTRA_ACTIONS, JsonValue.wrapOpt(actionPayload).toString());

        return PendingIntent.getActivity(context, NOTIFICATION_ID, intent, 0);
    }
}
