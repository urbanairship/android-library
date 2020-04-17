/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;

/**
 * Package update receiver to automatically migrate Accengage to Airship.
 */
public class PackageUpdatedReceiver extends BroadcastReceiver {

    private static final String TAG = PackageUpdatedReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Logger.debug(TAG + " - Application has been updated");
    }

}
