/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import com.urbanairship.app.GlobalActivityMonitor

/**
 * Initializes any app level dependencies that do not depend on Airship being called.
 * Should be called as soon as possible in the app's lifecycle.
 */
internal object AirshipAppBootstrap {

    fun init(context: Context) {
        // Call it to create instance
        GlobalActivityMonitor.shared(context)
    }
}
