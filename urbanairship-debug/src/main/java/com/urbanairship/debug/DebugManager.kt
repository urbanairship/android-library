/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship
import com.urbanairship.debug.event.EventListFragment
import com.urbanairship.debug.event.persistence.EventEntity
import com.urbanairship.debug.push.persistence.PushEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Debug manager. Initialized by UAirship instance during takeOff.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DebugManager(
    context: Context,
    preferenceDataStore: PreferenceDataStore,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    companion object {
        const val TRIM_PUSHES_COUNT = 50L
    }

    override fun onAirshipReady(airship: UAirship) {
        super.onAirshipReady(airship)

        airship.pushManager.addInternalPushListener { message, _ ->
            scope.launch {
                ServiceLocator.shared(context).getPushDao().insertPush(PushEntity(message))
            }
        }

        scope.launch {
            val storageDays = ServiceLocator.shared(context)
                    .sharedPreferences.getInt(EventListFragment.STORAGE_DAYS_KEY, EventListFragment.DEFAULT_STORAGE_DAYS)

            ServiceLocator.shared(context)
                    .getEventRepository()
                    .trimOldEvents(storageDays)

            ServiceLocator.shared(context)
                    .getPushDao()
                    .trimPushes(TRIM_PUSHES_COUNT)
        }


        scope.launch {
            airship.analytics.events.collectLatest {
                ServiceLocator.shared(context)
                    .getEventDao()
                    .insertEvent(EventEntity(it))
            }
        }
    }
}
