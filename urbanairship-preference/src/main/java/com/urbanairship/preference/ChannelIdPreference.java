/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.util.AttributeSet;
import com.urbanairship.UAirship;

import java.lang.ref.WeakReference;

/**
 * The Channel ID preference.
 */
public class ChannelIdPreference extends Preference {

    /**
     * Maximum times to check for Channel ID value when
     * it should be enabled, yet the Channel ID is not populated
     */
    private final static int CHANNEL_ID_MAX_RETRIES = 4;

    /**
     * Delay in milliseconds for each Channel ID retry
     * attempts
     */
    private final static int CHANNEL_ID_RETRY_DELAY = 1000;

    private int channelRetries;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ChannelIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ChannelIdPreference(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ChannelIdPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        final WeakReference<ChannelIdPreference> weakThis = new WeakReference<>(this);
        final Handler handler = new Handler(Looper.getMainLooper());

        handler.post(new Runnable() {
            @Override
            public void run() {
                ChannelIdPreference preference = weakThis.get();
                if (preference == null) {
                    return;
                }

                if (UAirship.shared().getPushManager().getChannelId() != null) {
                    preference.setSummary(UAirship.shared().getPushManager().getChannelId());
                } else if (preference.channelRetries < CHANNEL_ID_MAX_RETRIES) {
                    handler.postDelayed(this, CHANNEL_ID_RETRY_DELAY);
                    preference.channelRetries++;
                }
            }
        });
    }
}
