/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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
