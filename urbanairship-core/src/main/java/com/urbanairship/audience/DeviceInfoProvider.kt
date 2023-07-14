/* Copyright Airship and Contributors */

package com.urbanairship.audience

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import com.urbanairship.PrivacyManager
import com.urbanairship.UAirship
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Device info provider.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DeviceInfoProvider {
    public fun userCutOffDate(context: Context): Long
    public fun getUserLocals(context: Context): LocaleListCompat
    public fun isFeatureEnabled(@PrivacyManager.Feature feature: Int): Boolean
    public suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus>
    public suspend fun getStableContactId(): String

    public val isNotificationsOptedIn: Boolean
    public val channelTags: Set<String>
    public val appVersion: Long
    public val channelId: String?

    public companion object {
        public fun legacy(): DeviceInfoProvider = DeviceInfoProviderImpl(
            notificationStatusFetcher = UAirship.shared().pushManager::areNotificationsOptedIn,
            privacyFeatureFetcher = UAirship.shared().privacyManager::isEnabled,
            channelTagsFetcher = UAirship.shared().channel::tags,
            channelIdFetcher = UAirship.shared().channel::id,
            versionFetcher = UAirship.shared().applicationMetrics::getCurrentAppVersion,
            permissionsManager = UAirship.shared().permissionsManager,
            contactIdFetcher = UAirship.shared().contact::stableContactId
        )
    }
}

internal class DeviceInfoProviderImpl(
    private val notificationStatusFetcher: () -> Boolean,
    private val privacyFeatureFetcher: (Int) -> Boolean,
    private val channelTagsFetcher: () -> Set<String>,
    private val channelIdFetcher: () -> String?,
    private val versionFetcher: () -> Long,
    private val permissionsManager: PermissionsManager,
    private val contactIdFetcher: suspend () -> String
) : DeviceInfoProvider {

    override fun userCutOffDate(context: Context): Long {
        val packageName = context.packageName
        return context.packageManager.getPackageInfo(packageName, 0).firstInstallTime
    }

    override val isNotificationsOptedIn: Boolean
        get() = notificationStatusFetcher.invoke()

    override val channelTags: Set<String>
        get() = channelTagsFetcher.invoke()

    override val appVersion: Long
        get() = versionFetcher.invoke()

    override val channelId: String?
        get() = channelIdFetcher.invoke()

    override fun isFeatureEnabled(feature: Int): Boolean {
        return privacyFeatureFetcher.invoke(feature)
    }

    override fun getUserLocals(context: Context): LocaleListCompat {
        return ConfigurationCompat.getLocales(context.resources.configuration)
    }

    override suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus> {
        val resolver: suspend (Permission) -> PermissionStatus = {
            suspendCoroutine { continuation ->
                val result = permissionsManager.checkPermissionStatus(it).result
                continuation.resume(result ?: PermissionStatus.NOT_DETERMINED)
            }
        }

        return permissionsManager.configuredPermissions.associateWith { resolver(it) }
    }

    override suspend fun getStableContactId(): String = contactIdFetcher.invoke()
}
