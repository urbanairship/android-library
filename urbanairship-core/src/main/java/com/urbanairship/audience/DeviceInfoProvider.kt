/* Copyright Airship and Contributors */

package com.urbanairship.audience

import android.content.pm.PackageInfo
import androidx.annotation.RestrictTo
import androidx.core.content.pm.PackageInfoCompat
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.contacts.StableContactInfo
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Device info provider.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DeviceInfoProvider {

    public suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus>
    public suspend fun getStableContactInfo(): StableContactInfo
    public suspend fun getChannelId(): String

    public val isNotificationsOptedIn: Boolean
    public val channelTags: Set<String>
    public val appVersionName: String
    public val appVersionCode: Long
    public val platform: Platform
    public val channelCreated: Boolean
    public val analyticsEnabled: Boolean
    public val installDateMilliseconds: Long
    public val locale: Locale

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun newProvider(contactId: String? = null): DeviceInfoProvider {
            return DeviceInfoProviderImpl(contactId)
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun newCachingProvider(contactId: String? = null): DeviceInfoProvider {
            return CachingDeviceInfoProvider(DeviceInfoProviderImpl(contactId))
        }
    }
}

internal class DeviceInfoProviderImpl(private val contactId: String? = null) : DeviceInfoProvider {

    private val packageName = Airship.application.packageName
    private val packageInfo: PackageInfo? = Airship.application.packageManager
        .getPackageInfo(packageName, 0)
    private val appVersion: Long = Airship.application
        .packageManager
        .getPackageInfo(packageName, 0)
        ?.let { PackageInfoCompat.getLongVersionCode(it) }
        ?: -1

    override val installDateMilliseconds: Long
        get() = packageInfo?.firstInstallTime ?: 0

    override val isNotificationsOptedIn: Boolean
        get() = Airship.push.areNotificationsOptedIn()

    override val channelTags: Set<String>
        get() = Airship.channel.tags
    override val appVersionName: String
        get() = packageInfo?.versionName ?: ""
    override val appVersionCode: Long
        get() = appVersion

    override val platform: Platform
        get() = Airship.platform

    override val channelCreated: Boolean
        get() = Airship.channel.id != null
    override val analyticsEnabled: Boolean
        get() = Airship.analytics.isEnabled

    override val locale: Locale
        get() = Airship.localeManager.locale

    override suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus> {
        return Airship.permissionsManager.configuredPermissions.associateWith {
            Airship.permissionsManager.checkPermissionStatus(it)
        }
    }

    override suspend fun getStableContactInfo(): StableContactInfo {
        return Airship.contact.stableContactInfo().let {
            if (contactId != null && it.contactId != contactId) {
                StableContactInfo(contactId, null)
            } else {
                it
            }
        }
    }

    override suspend fun getChannelId(): String {
        return Airship.channel.channelIdFlow.filterNotNull().first()
    }
}

internal class CachingDeviceInfoProvider(
    private val deviceInfoProviderImpl: DeviceInfoProviderImpl,
) : DeviceInfoProvider {

    private val cachedPermissionStatus = OneTimeValueSus {
        deviceInfoProviderImpl.getPermissionStatuses()
    }

    private val cachedStableContactInfo = OneTimeValueSus {
        deviceInfoProviderImpl.getStableContactInfo()
    }

    private val cachedChannelId = OneTimeValueSus {
        deviceInfoProviderImpl.getChannelId()
    }

    private val cachedIsNotificationsOptedIn = OneTimeValue {
        deviceInfoProviderImpl.isNotificationsOptedIn
    }

    private val cachedChannelTags = OneTimeValue {
        deviceInfoProviderImpl.channelTags
    }

    private val cachedAppVersionName = OneTimeValue {
        deviceInfoProviderImpl.appVersionName
    }

    private val cachedAppVersionCode = OneTimeValue {
        deviceInfoProviderImpl.appVersionCode
    }

    private val cachedPlatform = OneTimeValue {
        deviceInfoProviderImpl.platform
    }

    private val cachedChannelCreated = OneTimeValue {
        deviceInfoProviderImpl.channelCreated
    }

    private val cachedAnalyticsEnabled = OneTimeValue {
        deviceInfoProviderImpl.analyticsEnabled
    }

    private val cachedInstallDateMilliseconds = OneTimeValue {
        deviceInfoProviderImpl.installDateMilliseconds
    }

    private val cachedLocale = OneTimeValue {
        deviceInfoProviderImpl.locale
    }

    override suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus> = cachedPermissionStatus.getValue()
    override suspend fun getStableContactInfo(): StableContactInfo = cachedStableContactInfo.getValue()
    override suspend fun getChannelId(): String = cachedChannelId.getValue()

    override val isNotificationsOptedIn: Boolean
        get() = cachedIsNotificationsOptedIn.getValue()
    override val channelTags: Set<String>
        get() = cachedChannelTags.getValue()
    override val appVersionName: String
        get() = cachedAppVersionName.getValue()
    override val appVersionCode: Long
        get() = cachedAppVersionCode.getValue()
    override val platform: Platform
        get() = cachedPlatform.getValue()
    override val channelCreated: Boolean
        get() = cachedChannelCreated.getValue()
    override val analyticsEnabled: Boolean
        get() = cachedAnalyticsEnabled.getValue()
    override val installDateMilliseconds: Long
        get() = cachedInstallDateMilliseconds.getValue()
    override val locale: Locale
        get() = cachedLocale.getValue()
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
