/* Copyright Airship and Contributors */

package com.urbanairship.experiment

import android.content.Context
import android.os.Looper
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipLoopers
import com.urbanairship.Logger
import com.urbanairship.PreferenceDataStore
import com.urbanairship.remotedata.RemoteData

/**
 * Airship Experiment Manager.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ExperimentManager internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val remoteData: RemoteData,
    backgroundLooper: Looper
) : AirshipComponent(context, dataStore) {

    public constructor(context: Context, dataStore: PreferenceDataStore, remoteData: RemoteData) : this(
        context = context,
        dataStore = dataStore,
        remoteData = remoteData,
        backgroundLooper = AirshipLoopers.getBackgroundLooper()
    )

    init {
        Logger.v { "init" }
    }
}
