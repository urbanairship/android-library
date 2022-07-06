/* Copyright Airship and Contributors */

package com.urbanairship;

import android.content.Context;

import com.urbanairship.app.GlobalActivityMonitor;

import androidx.annotation.NonNull;

/**
 * Initializes any app level dependencies that do not depend on Airship being called.
 * Should be called as soon as possible in the app's lifecycle.
 */
class AirshipAppBootstrap {

    public static void init(@NonNull Context context) {
        GlobalActivityMonitor.shared(context);
    }

}
