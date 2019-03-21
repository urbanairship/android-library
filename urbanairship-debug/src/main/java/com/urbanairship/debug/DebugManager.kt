/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import android.support.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.debug.event.persistence.EventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Debug manager. Initialized by UAirship instance during takeOff.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DebugManager(context: Context, preferenceDataStore: PreferenceDataStore) : AirshipComponent(context, preferenceDataStore) {

    companion object {
        const val TRIM_EVENTS_COUNT = 100000L
        const val TRIM_PUSHES_COUNT = 50L

    }

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)

        GlobalScope.launch(Dispatchers.IO) {
            ServiceLocator.shared(context)
                    .getEventDao()
                    .trimEvents(TRIM_EVENTS_COUNT)

            ServiceLocator.shared(context)
                    .getPushDao()
                    .trimPushes(TRIM_PUSHES_COUNT)
        }

        airship.analytics.addEventListener { event, session ->
            GlobalScope.launch(Dispatchers.IO) {
                ServiceLocator.shared(context)
                        .getEventDao()
                        .insertEvent(EventEntity(event, session))
            }
        }
    }

}
