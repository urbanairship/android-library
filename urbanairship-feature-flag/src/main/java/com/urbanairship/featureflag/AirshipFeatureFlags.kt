package com.urbanairship.featureflag

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Airship Feature Flags provider.
 */
class AirshipFeatureFlags @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData
) : AirshipComponent(context, dataStore) {

    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.FEATURE_FLAGS

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public override fun init() {
        super.init()

        UALog.d { "init" }
    }
}
