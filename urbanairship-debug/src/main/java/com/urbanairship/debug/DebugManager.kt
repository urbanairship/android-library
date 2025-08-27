/* Copyright Airship and Contributors */

package com.urbanairship.debug

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.Airship
import com.urbanairship.analytics.Analytics
import com.urbanairship.debug.ui.events.EventEntity
import com.urbanairship.debug.ui.push.PushEntity
import com.urbanairship.push.PushManager
import com.urbanairship.push.PushMessage
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug manager. Initialized by Airship instance during takeOff.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal class DebugManager(
    context: Context,
    preferenceDataStore: PreferenceDataStore,
    internal val remoteData: RemoteData,
    internal val pushManager: PushManager,
    internal val analytics: Analytics,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AirshipComponent(context, preferenceDataStore) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    companion object {
        fun shared(): DebugManager {
            return Airship.shared().requireComponent(DebugManager::class.java)
        }
    }

    override fun onAirshipReady() {

        scope.launch {
            pushManager.addPushListener { message: PushMessage, _: Boolean ->
                ServiceLocator.shared(context)
                    .getPushDao()
                    .insertPush(PushEntity(message))
            }

            analytics.events.collect {
                ServiceLocator.shared(context)
                    .getEventDao()
                    .insertEvent(EventEntity(it))
            }
        }
    }
}
