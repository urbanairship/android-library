/* Copyright Airship and Contributors */
package com.urbanairship.automation.engine

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PrivacyManager
import com.urbanairship.preferences.AsyncPrefKey
import com.urbanairship.preferences.PreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Tracks app-update state for the automation event feed. */
internal class ApplicationMetrics(
    private val context: Context,
    private val dataStore: PreferenceStore,
    private val privacyManager: PrivacyManager,
    private val scope: CoroutineScope = CoroutineScope(AirshipDispatchers.IO)
) {

    private val _appVersionUpdated = MutableStateFlow(false)

    var previousAppVersion: Long? = null
        private set
    var previousAppVersionName: String? = null
        private set

    private val privacyManagerListener = {
        scope.launch { updateData() }
        Unit
    }

    /** Tracks the in-flight init [updateData] so [isAppVersionUpdated] can wait it out. */
    private val initJob: Job = scope.launch { updateData() }

    init {
        privacyManager.addListener(privacyManagerListener)
    }

    internal fun tearDown() {
        privacyManager.removeListener(privacyManagerListener)
    }

    /**
     * Whether the app version code is higher than the last persisted value (i.e. the user
     * upgraded the app since the last run).
     *
     * Requires [PrivacyManager.Feature.IN_APP_AUTOMATION] or [PrivacyManager.Feature.ANALYTICS]
     * to be enabled.
     */
    suspend fun isAppVersionUpdated(): Boolean {
        initJob.join()
        return _appVersionUpdated.value
    }

    /** Gets the current app version code. */
    val currentAppVersion: Long
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            ?.let { PackageInfoCompat.getLongVersionCode(it) }
            ?: -1

    /** Gets the current app version name. */
    val currentAppVersionName: String
        get() = context.packageManager
            .getPackageInfo(context.packageName, 0)
            ?.versionName ?: ""



    private suspend fun updateData() {
        if (privacyManager.isAnyEnabled(PrivacyManager.Feature.IN_APP_AUTOMATION, PrivacyManager.Feature.ANALYTICS)) {
            val current = currentAppVersion
            val last = dataStore.get(LAST_APP_VERSION_KEY) ?: -1L

            if (last > -1 && current > last) {
                _appVersionUpdated.value = true
                previousAppVersion = last
                previousAppVersionName = dataStore.get(LAST_APP_VERSION_NAME_KEY)
            }

            dataStore.put(LAST_APP_VERSION_KEY, current)
            currentAppVersionName.takeUnless { it.isBlank() }?.let {
                dataStore.put(LAST_APP_VERSION_NAME_KEY, it)
            }
        } else {
            dataStore.remove(LAST_APP_VERSION_KEY)
            dataStore.remove(LAST_APP_VERSION_NAME_KEY)
        }
    }

    internal companion object {
        private val LAST_APP_VERSION_KEY = AsyncPrefKey.long("com.urbanairship.application.metrics.APP_VERSION")
        private val LAST_APP_VERSION_NAME_KEY = AsyncPrefKey.string("com.urbanairship.application.metrics.APP_VERSION_NAME")
    }
}
