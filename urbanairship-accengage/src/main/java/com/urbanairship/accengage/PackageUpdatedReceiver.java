/* Copyright Airship and Contributors */

package com.urbanairship.accengage;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Package update receiver to automatically migrate Accengage to Airship.
 */
public class PackageUpdatedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull final Context context, @Nullable final Intent intent) {
        Logger.debug("Application has been updated");
    }

}
