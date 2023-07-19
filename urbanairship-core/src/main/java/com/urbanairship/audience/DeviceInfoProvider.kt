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
import com.urbanairship.util.PlatformUtils
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    public val platform: String

    public suspend fun snapshot(context: Context): DeviceInfoProvider

    public companion object {
        public fun legacy(): DeviceInfoProvider = DeviceInfoProviderImpl(
            notificationStatusFetcher = UAirship.shared().pushManager::areNotificationsOptedIn,
            privacyFeatureFetcher = UAirship.shared().privacyManager::isEnabled,
            channelTagsFetcher = UAirship.shared().channel::tags,
            channelIdFetcher = UAirship.shared().channel::id,
            versionFetcher = UAirship.shared().applicationMetrics::getCurrentAppVersion,
            permissionsManager = UAirship.shared().permissionsManager,
            contactIdFetcher = UAirship.shared().contact::stableContactId,
            platform = PlatformUtils.asString(UAirship.shared().platformType)
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
    private val contactIdFetcher: suspend () -> String,
    override val platform: String
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

    override suspend fun snapshot(context: Context): DeviceInfoProvider {
        return CachedDeviceInfoProvider(
            cutOffTime = OneTimeValue { userCutOffDate(context) },
            localeList = OneTimeValue { getUserLocals(context) },
            privacyFeatureFetcher = privacyFeatureFetcher,
            permissionStatuses = OneTimeValueSus { getPermissionStatuses() },
            stableContactId = OneTimeValueSus { getStableContactId() },
            cachedIsNotificationsOptedIn = OneTimeValue { isNotificationsOptedIn },
            cachedChannelTags = OneTimeValue { channelTags },
            cachedAppVersion = OneTimeValue { appVersion },
            cachedChannelId = OneTimeValue { channelId },
            cachedPlatform = OneTimeValue { platform }
        )
    }
}

internal class CachedDeviceInfoProvider(
    private val cutOffTime: OneTimeValue<Long>,
    private val localeList: OneTimeValue<LocaleListCompat>,
    private val privacyFeatureFetcher: (Int) -> Boolean,
    private val permissionStatuses: OneTimeValueSus<Map<Permission, PermissionStatus>>,
    private val stableContactId: OneTimeValueSus<String>,
    cachedIsNotificationsOptedIn: OneTimeValue<Boolean>,
    cachedChannelTags: OneTimeValue<Set<String>>,
    cachedAppVersion: OneTimeValue<Long>,
    cachedChannelId: OneTimeValue<String?>,
    cachedPlatform: OneTimeValue<String>
) : DeviceInfoProvider {
    override fun userCutOffDate(context: Context): Long = cutOffTime.getValue()
    override fun getUserLocals(context: Context): LocaleListCompat = localeList.getValue()
    override fun isFeatureEnabled(feature: Int): Boolean = privacyFeatureFetcher(feature)
    override suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus> = permissionStatuses.getValue()
    override suspend fun getStableContactId(): String = stableContactId.getValue()

    override val isNotificationsOptedIn: Boolean = cachedIsNotificationsOptedIn.getValue()
    override val channelTags: Set<String> = cachedChannelTags.getValue()
    override val appVersion: Long = cachedAppVersion.getValue()
    override val channelId: String? = cachedChannelId.getValue()
    override val platform: String = cachedPlatform.getValue()

    override suspend fun snapshot(context: Context): DeviceInfoProvider = this
}

internal class OneTimeValue<T>(
    private var fetcher: () -> T
) {
    private var lock = Any()
    private var cached: T? = null

    fun getValue(): T {
        synchronized(lock) {
            val result = cached ?: fetcher()
            cached = result
            return result
        }
    }
}

internal class OneTimeValueSus<T>(
    private var fetcher: suspend () -> T
) {
    private var lock = Mutex()
    private var cached: T? = null

    suspend fun getValue(): T {
        lock.withLock {
            val result = cached ?: fetcher()
            cached = result
            return result
        }
    }
}
