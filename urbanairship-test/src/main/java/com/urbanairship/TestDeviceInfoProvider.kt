package com.urbanairship

import android.content.Context
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.locale.LocaleManager
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.permission.PermissionsManager
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import org.mockito.Mockito.mock

public data class TestDeviceInfoProvider(
    private val notificationStatusFetcher: () -> Boolean,
    private val privacyFeatureFetcher: (Int) -> Boolean,
    private val channelTagsFetcher: () -> Set<String>,
    private val channelIdFetcher: () -> String?,
    private val versionFetcher: () -> Long,
    private val permissionsManager: PermissionsManager,
    private val contactIdFetcher: suspend () -> String,
    override val platform: String,
    private val localeManager: LocaleManager
) : DeviceInfoProvider {

    @JvmOverloads
    constructor(
        notificationStatus: Boolean,
        privacyFeature: (Int) -> Boolean,
        channelTags: Set<String>,
        channelId: String?,
        version: Long,
        contactId: String,
        platform: String,
        permissionsManager: PermissionsManager = mock(PermissionsManager::class.java),
        localeManager: LocaleManager = mock(LocaleManager::class.java)
    ) : this(
        notificationStatusFetcher = { notificationStatus },
        privacyFeatureFetcher = privacyFeature,
        channelTagsFetcher = { channelTags },
        channelIdFetcher = { channelId },
        versionFetcher = { version },
        permissionsManager = permissionsManager,
        contactIdFetcher = suspend { contactId },
        platform = platform,
        localeManager = localeManager
    )

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

    override fun getUserLocale(context: Context): Locale {
        return localeManager.locale
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
        return this.copy()
    }
}
