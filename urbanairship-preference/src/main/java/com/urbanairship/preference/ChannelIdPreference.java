/* Copyright Airship and Contributors */

package com.urbanairship.preference;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;

import com.urbanairship.UAirship;
import com.urbanairship.channel.AirshipChannelListener;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

/**
 * The Channel ID preference.
 */
public class ChannelIdPreference extends Preference {

    private final AirshipChannelListener channelListener = new AirshipChannelListener() {
        @Override
        public void onChannelCreated(@NonNull String channelId) { notifyChangedMainThread(); }

        @Override
        public void onChannelUpdated(@NonNull String channelId) { notifyChangedMainThread(); }
    };

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
    @Nullable
    public CharSequence getSummary() {
        return UAirship.shared().getChannel().getId();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        UAirship.shared().getChannel().addChannelListener(channelListener);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        UAirship.shared().getChannel().removeChannelListener(channelListener);
    }

    private void notifyChangedMainThread() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                notifyChanged();
            }
        });
    }
}
