/* Copyright Urban Airship and Contributors */

package com.urbanairship.debug

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.support.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.debug.event.ServiceLocator
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
    }

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)

        val pm = UAirship.getPackageManager()
        val component = ComponentName(UAirship.getApplicationContext(), DebugActivity::class.java)

        pm.setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)

        GlobalScope.launch(Dispatchers.IO) {
            ServiceLocator.shared(context)
                    .getEventDao()
                    .trimEvents(TRIM_EVENTS_COUNT)
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
