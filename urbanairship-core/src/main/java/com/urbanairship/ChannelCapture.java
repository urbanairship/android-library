/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;

import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.SimpleApplicationListener;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.util.UAStringUtil;

import java.util.Calendar;

import androidx.annotation.NonNull;

/**
 * ChannelCapture detects a knock when the application is foregrounded 6 times in 30 seconds.
 * When a knock is detected, it writes the channel ID to the clipboard as ua:<channel_id>.
 */
public class ChannelCapture extends AirshipComponent {

    private final Context context;
    private final AirshipConfigOptions configOptions;
    private final AirshipChannel airshipChannel;
    private ClipboardManager clipboardManager;
    private final ApplicationListener listener;
    private final ActivityMonitor activityMonitor;

    private static final long KNOCKS_MAX_TIME_IN_MS = 30000;
    private static final int KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE = 6;
    private int indexOfKnocks;
    private long[] knockTimes;

    private boolean enabled;

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
        this.activityMonitor = activityMonitor;

        this.knockTimes = new long[KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE];
        this.listener = new SimpleApplicationListener() {
            @Override
            public void onForeground(long time) {
                countForeground(time);
            }
        };
    }

    @Override
    protected void init() {
        super.init();

        enabled = configOptions.channelCaptureEnabled;

        activityMonitor.addApplicationListener(listener);
    }

    /**
     * Count the number of foregrounds to perform the knock.
     * @param time the timestamp to when the app has been foregrounded.
     */
    private void countForeground(long time) {
        if (!isEnabled()) {
            return;
        }

        if (indexOfKnocks >= KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE) {
            indexOfKnocks = 0;
        }
        knockTimes[indexOfKnocks] = time;
        indexOfKnocks++;
        if (checkKnock()) {
            writeClipboard();
        }
    }

    /**
     * Check if a knock should be launched.
     * @return {@code true} if there is a knock, otherwise return {@code false}.
     */
    private boolean checkKnock() {
        long currentTime = Calendar.getInstance().getTimeInMillis();

        for (long knockTime : knockTimes) {
            if (knockTime + KNOCKS_MAX_TIME_IN_MS < currentTime) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the clipboard is available and perform the channel capture.
     */
    private void writeClipboard() {
        if (clipboardManager == null) {
            // Since ClipboardManager initialization can fail deep in the android platform
            // stack, catch any unanticipated errors here.
            try {
                clipboardManager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            } catch (Exception e) {
                Logger.error(e, "Unable to initialize clipboard manager: ");
            }
        }

        if (clipboardManager == null) {
            Logger.debug("Unable to attempt channel capture, clipboard manager uninitialized");
            return;
        }

        // reset the knock counters so it takes 6 new knocks to capture channel
        knockTimes = new long[KNOCKS_TO_TRIGGER_CHANNEL_CAPTURE];
        indexOfKnocks = 0;

        String channel = airshipChannel.getId();
        String channelIdForClipboard = UAStringUtil.isEmpty(channel) ? "ua:" : "ua:" + channel;

        try {
            new Handler(AirshipLoopers.getBackgroundLooper()).post(new Runnable() {
                @Override
                public void run() {
                    ClipData clipData = ClipData.newPlainText("UA Channel ID", channelIdForClipboard);
                    clipboardManager.setPrimaryClip(clipData);
                    Logger.debug("Channel ID copied to clipboard");
                }
            });
        } catch (Exception e) {
            Logger.warn(e, "Channel capture failed! Unable to copy Channel ID to clipboard.");
        }

    }

    /**
     * Sets channel capture enabled
     * @param enabled {@code true} to enable channel capture, {@code false} to disable.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns {@code true} if channel capture is enabled, {@link com.urbanairship.AirshipConfigOptions#channelCaptureEnabled}
     * is set to {@code true}, otherwise {@code false}.
     * @return {@code true} if channel capture is enabled, otherwise {@code false}.
     */
    public boolean isEnabled() {
        return this.enabled;
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeApplicationListener(listener);
    }

}
