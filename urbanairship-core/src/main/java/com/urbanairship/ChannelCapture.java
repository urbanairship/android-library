/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationManagerCompat;

/**
 * ChannelCapture checks the device clipboard for a String that is prefixed by
 * an expected token on app foreground and posts a notification
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
    private final AirshipChannel airshipChannel;
    private ClipboardManager clipboardManager;
    private final ApplicationListener listener;
    private final ActivityMonitor activityMonitor;
    private final PreferenceDataStore preferenceDataStore;

    Executor executor = AirshipExecutors.THREAD_POOL_EXECUTOR;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param configOptions The airship config options.
     * @param airshipChannel The airship channel.
     * @param activityMonitor The activity monitor instance.
     */
    ChannelCapture(@NonNull Context context, @NonNull AirshipConfigOptions configOptions,
                   @NonNull AirshipChannel airshipChannel, @NonNull PreferenceDataStore preferenceDataStore,
                   @NonNull ActivityMonitor activityMonitor) {
        super(context, preferenceDataStore);
        this.context = context.getApplicationContext();
        this.configOptions = configOptions;
        this.airshipChannel = airshipChannel;

        this.listener = new SimpleApplicationListener() {
            @Override
            public void onForeground(long time) {
                checkClipboard();
            }
        };

        this.preferenceDataStore = preferenceDataStore;
        this.activityMonitor = activityMonitor;
    }

    @Override
    protected void init() {
        super.init();

        // ClipboardManager must be initialized on a thread with a prepared looper
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (activityMonitor.isAppForegrounded()) {
                    checkClipboard();
                }

                activityMonitor.addApplicationListener(listener);
            }
        });
    }

    /**
     * Enable channel capture for the specified period of time.
     *
     * @param duration The duration of time.
     * @param unit The time unit.
     */
    public void enable(long duration, @NonNull TimeUnit unit) {
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
        activityMonitor.removeApplicationListener(listener);
    }

    private void checkClipboard() {
        if (clipboardManager == null) {
            // Since ClipboardManager initialization can fail deep in the android platform
            // stack, catch any unanticipated errors here.
            try {
                Context ctx = ChannelCapture.this.context;
                clipboardManager = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            } catch (Exception e) {
                Logger.error(e, "Unable to initialize clipboard manager: ");
            }
        }

        if (clipboardManager == null) {
            Logger.debug("Unable to attempt channel capture, clipboard manager uninitialized");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                attemptChannelCapture();
            }
        });
    }

    /**
     * Checks the clipboard for the token and starts the activity if the token is
     * available.
     */
    private void attemptChannelCapture() {
        String channel = airshipChannel.getId();

        if (UAStringUtil.isEmpty(channel)) {
            return;
        }

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
            Logger.debug(e, "Unable to read clipboard.");
            return;
        }

        String decodedClipboardString = UAStringUtil.base64DecodedString(clipboardText);
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
            Logger.debug(e, "Unable to clear clipboard.");
        }

        startChannelCaptureActivity(channel, url);
    }

    /**
     * Create the intent that launches the {@link ChannelCaptureActivity}
     *
     * @param channel The channel string.
     * @param url The channel url.
     */
    private void startChannelCaptureActivity(@Nullable String channel, @Nullable String url) {
        Intent intent = new Intent(context, ChannelCaptureActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(CHANNEL, channel)
                .putExtra(URL, url);
        context.startActivity(intent);
    }

    /**
     * Generates the expected clipboard token.
     *
     * @return The generated clipboard token.
     */
    @NonNull
    private String generateToken() {
        byte[] appKeyBytes = configOptions.appKey.getBytes();
        byte[] appSecretBytes = configOptions.appSecret.getBytes();

        StringBuilder code = new StringBuilder();

        for (int i = 0; i < appKeyBytes.length; i++) {
            byte b = (byte) (appKeyBytes[i] ^ appSecretBytes[i % appSecretBytes.length]);
            code.append(String.format("%02x", b));
        }

        return code.toString();
    }

}
