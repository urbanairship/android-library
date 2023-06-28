/* Copyright Airship and Contributors */

package com.urbanairship.liveupdate

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.BuildConfig
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.modules.Module
import com.urbanairship.modules.liveupdate.LiveUpdateModuleFactory
import com.urbanairship.push.PushManager

/**
 * Live Update module factory implementation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LiveUpdateModuleFactoryImpl : LiveUpdateModuleFactory {

    override val airshipVersion: String = BuildConfig.AIRSHIP_VERSION
    override val packageVersion: String = BuildConfig.SDK_VERSION

    override fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        config: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        airshipChannel: AirshipChannel,
        pushManager: PushManager
    ): Module {
        val manager = LiveUpdateManager(
            context = context,
            dataStore = dataStore,
            config = config,
            privacyManager = privacyManager,
            channel = airshipChannel,
            pushManager = pushManager
        )
        return Module.singleComponent(manager, 0)
    }
}
