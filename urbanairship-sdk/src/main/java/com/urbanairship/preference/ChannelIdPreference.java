/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

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

    private static final String CONTENT_DESCRIPTION = "CHANNEL_ID";

    private int channelRetries;

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ChannelIdPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ChannelIdPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ChannelIdPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onAttachedToActivity() {
        super.onAttachedToActivity();

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

    @Override
    public View onCreateView(ViewGroup parent) {
        View view = super.onCreateView(parent);
        view.setContentDescription(CONTENT_DESCRIPTION);
        return view;
    }
}
