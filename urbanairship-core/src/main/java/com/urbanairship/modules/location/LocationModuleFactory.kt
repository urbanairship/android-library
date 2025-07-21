/* Copyright Airship and Contributors */
package com.urbanairship.modules.location

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.permission.PermissionsManager

/**
 * Location module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LocationModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        permissionsManager: PermissionsManager
    ): LocationModule
}
