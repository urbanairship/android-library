package com.urbanairship.modules.preferencecenter

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipVersionInfo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.modules.Module
import com.urbanairship.remotedata.RemoteData

/**
 * PreferenceCenter module factory.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface PreferenceCenterModuleFactory : AirshipVersionInfo {

    public fun build(
        context: Context,
        dataStore: PreferenceDataStore,
        privacyManager: PrivacyManager,
        remoteData: RemoteData,
        inputValidator: AirshipInputValidation.Validator
    ): Module
}
