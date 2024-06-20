/* Copyright Airship and Contributors */
package com.urbanairship

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

/**
 * Airship initializer.
 */
public open class AirshipInitializer : Initializer<Boolean> {

    override fun create(context: Context): Boolean {
        Autopilot.automaticTakeOff((context.applicationContext as Application), true)
        return UAirship.isTakingOff() || UAirship.isFlying()
    }

    override fun dependencies(): List<Class<out Initializer<*>?>> {
        return emptyList()
    }
}

/**
 * No dependency Airship initializer.
 * @deprecated
 */

@Deprecated("AirshipInitializer and NoDependencyAirshipInitializer are now the same. Use AirshipInitializer instead.")
public class NoDependencyAirshipInitializer: AirshipInitializer()
