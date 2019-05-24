/* Copyright Airship and Contributors */

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
import com.urbanairship.push.RegistrationListener;

/**
 * The Channel ID preference.
 */
public class ChannelIdPreference extends Preference {

    private RegistrationListener registrationListener = new RegistrationListener() {
        @Override
        public void onChannelCreated(@NonNull String channelId) { notifyChangedMainThread(); }

        @Override
        public void onChannelUpdated(@NonNull String channelId) { notifyChangedMainThread(); }

        @Override
        public void onPushTokenUpdated(@NonNull String token) {}
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
        return UAirship.shared().getPushManager().getChannelId();
    }

    @Override
    public void onAttached() {
        super.onAttached();
        UAirship.shared().getPushManager().addRegistrationListener(registrationListener);
    }

    @Override
    public void onDetached() {
        super.onDetached();
        UAirship.shared().getPushManager().removeRegistrationListener(registrationListener);
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
