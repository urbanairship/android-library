/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.debug.ui.events.EventEntity
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug manager. Initialized by UAirship instance during takeOff.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DebugManager(
    context: Context,
    preferenceDataStore: PreferenceDataStore,
    internal val remoteData: RemoteData,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    companion object {
        fun shared(): DebugManager {
            return UAirship.shared().requireComponent(DebugManager::class.java)
        }
    }

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)

        scope.launch {
            airship.analytics.events.collect {
                ServiceLocator.shared(context)
                    .getEventDao()
                    .insertEvent(EventEntity(it))
            }
        }
    }
}
