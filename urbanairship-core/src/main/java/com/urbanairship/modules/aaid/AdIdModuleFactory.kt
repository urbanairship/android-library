/* Copyright Airship and Contributors */
package com.urbanairship.modules.aaid

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.analytics.Analytics
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.modules.Module

/**
 * Ad Id module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface AdIdModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager,
        analytics: Analytics
    ): Module
}
