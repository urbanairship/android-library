/* Copyright Airship and Contributors */
package com.urbanairship.modules.liveupdate

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.modules.Module
import com.urbanairship.push.PushManager

/**
 * Live Update module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface LiveUpdateModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager
    ): Module
}
