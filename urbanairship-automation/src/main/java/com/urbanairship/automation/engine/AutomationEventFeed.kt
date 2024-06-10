/* Copyright Airship and Contributors */

package com.urbanairship.automation.engine

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.AirshipEventFeed
import com.urbanairship.analytics.EventType
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.EventAutomationTriggerType
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

internal data class TriggerableState(
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
internal sealed class AutomationEvent {

    internal data class Event(
        val triggerType: EventAutomationTriggerType,
        val data: JsonValue? = null,
        val value: Double = 1.0
    ) : AutomationEvent()

    internal data class StateChanged(
        val state: TriggerableState
    ) : AutomationEvent()

    internal val isStateEvent: Boolean
        get() = when(this) {
            is StateChanged -> true
            else -> false
        }

    internal val eventData: JsonValue?
        get() = when(this) {
            is StateChanged -> null
            is Event -> this.data
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

                stream.emit(AutomationEvent.Event(EventAutomationTriggerType.APP_INIT))

                if (applicationMetrics.appVersionUpdated) {
                    appSessionState.update { it.copy(versionUpdated = applicationMetrics.currentAppVersion.toString()) }
                }
            }

            merge(
                appSessionState.map { listOf(AutomationEvent.StateChanged(it)) },

                activityMonitor.foregroundState.map {
                    val event = if (it) {
                        AutomationEvent.Event(EventAutomationTriggerType.FOREGROUND)
                    } else {
                        AutomationEvent.Event(EventAutomationTriggerType.BACKGROUND)
                    }
                    listOf(event)
                },

                eventFeed.events.map {
                    it.toAutomationEvents
                }
            ).collect { events ->
                events?.forEach { event ->
                    stream.emit(event)

                    if (event is AutomationEvent.Event) {
                        when (event.triggerType) {
                            EventAutomationTriggerType.FOREGROUND -> appSessionState.update { it.copy(appSessionID = UUID.randomUUID().toString()) }
                            EventAutomationTriggerType.BACKGROUND -> appSessionState.update { it.copy(appSessionID = null) }
                            else -> {}
                        }
                    }

                }
            }
        }
    }

    internal fun detach() {
        subscription?.cancel()
    }
}

internal val AirshipEventFeed.Event.toAutomationEvents: List<AutomationEvent.Event>?
    get() {
        return when (this) {
            is AirshipEventFeed.Event.Screen -> {
                listOf(
                    AutomationEvent.Event(
                        triggerType = EventAutomationTriggerType.SCREEN,
                        data = JsonValue.wrap(this.screen)
                    )
                )
            }

            is AirshipEventFeed.Event.Analytics -> {
                when (this.eventType) {
                   EventType.REGION_ENTER -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.REGION_ENTER,
                                data = this.data
                            )
                        )
                    }

                    EventType.REGION_EXIT -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.REGION_EXIT,
                                data = this.data
                            )
                        )
                    }

                    EventType.CUSTOM_EVENT -> {
                        listOf(
                            AutomationEvent.Event(
                                EventAutomationTriggerType.CUSTOM_EVENT_COUNT, data = this.data
                            ), AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.CUSTOM_EVENT_VALUE,
                                data = this.data,
                                value = this.value ?: 1.0
                            )
                        )
                    }

                    EventType.FEATURE_FLAG_INTERACTION -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.FEATURE_FLAG_INTERACTION,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_DISPLAY -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_DISPLAY,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_RESOLUTION -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_RESOLUTION,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_BUTTON_TAP -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_BUTTON_TAP,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PERMISSION_RESULT -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_PERMISSION_RESULT,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_FORM_DISPLAY -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_FORM_DISPLAY,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_FORM_RESULT -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_FORM_RESULT,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_GESTURE -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_GESTURE,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PAGER_COMPLETED -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType =  EventAutomationTriggerType.IN_APP_PAGER_COMPLETED,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PAGER_SUMMARY -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_PAGER_SUMMARY,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PAGE_SWIPE -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_PAGE_SWIPE,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PAGE_VIEW -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_PAGE_VIEW,
                                data = this.data
                            )
                        )
                    }

                    EventType.IN_APP_PAGE_ACTION -> {
                        listOf(
                            AutomationEvent.Event(
                                triggerType = EventAutomationTriggerType.IN_APP_PAGE_ACTION,
                                data = this.data
                            )
                        )
                    }
                    else -> { null }
                }
            }
        }
    }
