package com.urbanairship.automation

import com.urbanairship.UAirship
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.util.PlatformUtils
import java.util.Locale

public class TestDeviceInfoProvider @JvmOverloads constructor(
    public override var isNotificationsOptedIn: Boolean = false,
    public override var channelTags: Set<String> = emptySet(),
    public override var appVersionName: String = "some-version-name",
    public override var appVersionCode: Long = 1,
    public override var platform: String = PlatformUtils.asString(UAirship.ANDROID_PLATFORM),
    public override var channelCreated: Boolean = true,
    public override var analyticsEnabled: Boolean = true,
    public override var installDateMilliseconds: Long = 0,
    public override var locale: Locale = Locale.US,
    public var permissionMap: Map<Permission, PermissionStatus> = emptyMap(),
    public var stableContactId: String = "stable-contact-id",
    public var channelId: String = "channel-id"
) : DeviceInfoProvider {

    override suspend fun getPermissionStatuses(): Map<Permission, PermissionStatus> {
        return permissionMap
    }

    override suspend fun getStableContactId(): String {
        return stableContactId
    }

    override suspend fun getChannelId(): String {
        return channelId
    }
}
