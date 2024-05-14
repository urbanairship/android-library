package com.urbanairship.automation

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class TriggerableState(
    val appSessionID: String? = null,
    val versionUpdated: String? = null
) : JsonSerializable {
    internal companion object {
        private const val APP_SESSION_ID = "appSessionID"
        private const val VERSION_UPDATED = "versionUpdated"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): TriggerableState {
            val content = value.requireMap()
            return TriggerableState(
                appSessionID = content.optionalField(APP_SESSION_ID),
                versionUpdated = content.optionalField(VERSION_UPDATED)
            )
        }
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        APP_SESSION_ID to appSessionID,
        VERSION_UPDATED to versionUpdated
    ).toJsonValue()

}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class AutomationEvent {
    internal data object Foreground : AutomationEvent()
    internal data object Background : AutomationEvent()
    internal data object AppInit : AutomationEvent()
    internal data class CoreEvent(val airshipEvent: AirshipEventFeed.Event): AutomationEvent()
    internal data class StateChanged(val state: TriggerableState) : AutomationEvent()

    internal fun reportPayload(): JsonValue? {
        return when(this) {
            Foreground, Background, AppInit, is StateChanged -> null
            is CoreEvent -> when (this.airshipEvent) {
                is AirshipEventFeed.Event.CustomEvent -> this.airshipEvent.data.toJsonValue()
                is AirshipEventFeed.Event.FeatureFlagInteracted -> this.airshipEvent.data.toJsonValue()
                is AirshipEventFeed.Event.RegionExit -> this.airshipEvent.data.toJsonValue()
                is AirshipEventFeed.Event.RegionEnter -> this.airshipEvent.data.toJsonValue()
                is AirshipEventFeed.Event.ScreenTracked -> JsonValue.wrapOpt(this.airshipEvent.name)
            }
        }
    }

    internal fun isStateEvent(): Boolean {
        return when(this) {
            is StateChanged -> true
            else -> false
        }
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationEventFeed(
    private val applicationMetrics: ApplicationMetrics,
    private val activityMonitor: ActivityMonitor,
    private val eventFeed: AirshipEventFeed,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val stream = MutableSharedFlow<AutomationEvent>()
    private val appSessionState = MutableStateFlow(TriggerableState())
    private var subscription: Job? = null
    private var hasAttachedBefore = false


    internal val feed: Flow<AutomationEvent> = stream
        .onSubscription { attach() }

    internal fun attach() {
        if (subscription?.isActive == true) { return }

        subscription = scope.launch {
            if (!hasAttachedBefore) {
                hasAttachedBefore = true

                stream.emit(AutomationEvent.AppInit)

                if (applicationMetrics.appVersionUpdated) {
                    appSessionState.update { it.copy(versionUpdated = applicationMetrics.currentAppVersion.toString()) }
                }
            }

            merge(
                appSessionState.map { AutomationEvent.StateChanged(it) },

                activityMonitor.foregroundState.map {
                    if (it) {
                        AutomationEvent.Foreground
                    } else {
                        AutomationEvent.Background
                    }
                },

                eventFeed.events.map { AutomationEvent.CoreEvent(it) }
            ).collect { event ->
                stream.emit(event)

                when (event) {
                    is AutomationEvent.Foreground -> appSessionState.update { it.copy(appSessionID = UUID.randomUUID().toString()) }
                    is AutomationEvent.Background -> appSessionState.update { it.copy(appSessionID = null) }
                    else -> {}
                }
            }
        }
    }

    internal fun detach() {
        subscription?.cancel()
    }
}
