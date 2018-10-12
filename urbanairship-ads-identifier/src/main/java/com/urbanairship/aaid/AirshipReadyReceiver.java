/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.aaid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.UAirship;

/**
 * Airship ready receiver. Used to set the airship instance on the {@link AdvertisingIdTracker}.
 * @hide
 */
public class AirshipReadyReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull final Context context, @Nullable Intent intent) {
        UAirship.shared(new UAirship.OnReadyCallback() {
            @Override
            public void onAirshipReady(@NonNull UAirship airship) {
                AdvertisingIdTracker.shared(context).setAirshipInstance(airship);
            }
        });

    }
}
