/* Copyright Airship and Contributors */
package com.urbanairship

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.content.pm.PackageInfoCompat
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener

/**
 * ApplicationMetrics stores metric information about the application.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ApplicationMetrics(
    private val context: Context,
    private val dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val activityMonitor: ActivityMonitor = GlobalActivityMonitor.shared(context)
) {

    private val applicationListener: ApplicationListener = object : SimpleApplicationListener() {
        override fun onForeground(milliseconds: Long) {
            if (privacyManager.isAnyEnabled(
                    PrivacyManager.Feature.ANALYTICS, PrivacyManager.Feature.IN_APP_AUTOMATION
                )
            ) {
                dataStore.put(LAST_OPEN_KEY, milliseconds)
            }
        }
    }

    private val privacyManagerListener = {
        updateData()
    }

    /**
     * Determines whether the app version has been updated.
     *
     * Requires [PrivacyManager.Feature.IN_APP_AUTOMATION] or [PrivacyManager.Feature.ANALYTICS] to be enabled.
     *
     * @return `true` if the app version has been updated, otherwise `false`.
     */
    public var appVersionUpdated: Boolean = false
        private set

    init {
        updateData()
        privacyManager.addListener(privacyManagerListener)
        activityMonitor.addApplicationListener(applicationListener)
    }

    internal fun tearDown() {
        privacyManager.removeListener(privacyManagerListener)
        activityMonitor.removeApplicationListener(applicationListener)
    }

    /**
     * Gets the time of the last open in milliseconds since
     * January 1, 1970 00:00:00.0 UTC.
     *
     * Requires [PrivacyManager.Feature.IN_APP_AUTOMATION] or [PrivacyManager.Feature.ANALYTICS] to be enabled.
     *
     * An application "open" is determined in [com.urbanairship.analytics.Analytics]
     * by tracking activity start and stops.  This ensures that background services or
     * broadcast receivers do not affect this number.  This number could be inaccurate
     * if analytic instrumentation is missing for activities when running on Android
     * ICS (4.0) or older.
     *
     * @return The time in milliseconds of the last application open, or -1 if the
     * last open has not been detected yet.
     *
     */
    @get:Deprecated("Will be removed in SDK 15.")
    public val lastOpenTimeMillis: Long
        get() = dataStore.getLong(LAST_OPEN_KEY, -1)

    /**
     * Gets the current app version.
     */
    public val currentAppVersion: Long
        get() = context
            .packageManager
            .getPackageInfo(context.packageName, 0)
            ?.let { PackageInfoCompat.getLongVersionCode(it) }
            ?: -1

    private val lastAppVersion: Long
        get() = dataStore.getLong(LAST_APP_VERSION_KEY, -1)

    private fun updateData() {
        if (privacyManager.isAnyEnabled(PrivacyManager.Feature.IN_APP_AUTOMATION, PrivacyManager.Feature.ANALYTICS)) {
            val currentAppVersion = this.currentAppVersion
            val lastAppVersion = lastAppVersion

            if (lastAppVersion > -1 && currentAppVersion > lastAppVersion) {
                appVersionUpdated = true
            }

            dataStore.put(LAST_APP_VERSION_KEY, currentAppVersion)
        } else {
            dataStore.remove(LAST_APP_VERSION_KEY)
            dataStore.remove(LAST_OPEN_KEY)
        }
    }

    internal companion object {
        private const val LAST_OPEN_KEY = "com.urbanairship.application.metrics.LAST_OPEN"
        private const val LAST_APP_VERSION_KEY = "com.urbanairship.application.metrics.APP_VERSION"
    }
}
