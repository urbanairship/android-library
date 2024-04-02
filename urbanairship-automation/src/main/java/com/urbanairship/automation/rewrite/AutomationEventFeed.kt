package com.urbanairship.automation.rewrite

import com.urbanairship.AirshipDispatchers
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.AnalyticsListener
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.json.JsonValue
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

internal data class TriggerableState(
    var appSessionID: String? = null,
    var versionUpdated: String? = null
)

internal sealed class AutomationEvent {
    data object Foreground : AutomationEvent()
    data object Background : AutomationEvent()
    data object AppInit : AutomationEvent()
    data class ScreenView(val name: String?): AutomationEvent()
    data class StateChanged(val state: TriggerableState) : AutomationEvent()
    data class RegionEnter(val data: JsonValue) : AutomationEvent()
    data class RegionExit(val data: JsonValue) : AutomationEvent()
    data class CustomEvent(val data: JsonValue, val count: Double?) : AutomationEvent()
    data class FeatureFlagInteracted(val data: JsonValue) : AutomationEvent()
}

private interface Cancellable {
    fun cancel()
}

internal class AutomationEventFeed(
    private val applicationMetrics: ApplicationMetrics,
    private val activityMonitor: ActivityMonitor,
    private val analytics: Analytics
) {
    private val stream = MutableSharedFlow<AutomationEvent>()
    private val appSessionState = TriggerableState()
    private var subscription: Cancellable? = null
    private var isFirstAttach = false

    val feed: Flow<AutomationEvent> = stream

    fun attach() {
        if (subscription != null) { return }

        if (!isFirstAttach) {
            isFirstAttach = true
            emit(AutomationEvent.AppInit)

            if (applicationMetrics.appVersionUpdated) {
                appSessionState.versionUpdated = applicationMetrics.currentAppVersion.toString()
                emit(AutomationEvent.StateChanged(appSessionState))
            }
        }

        subscription = startListeningForEvents()
    }

    fun detach() {
        subscription?.cancel()
    }

    private fun emit(event: AutomationEvent) {
        runBlocking { stream.emit(event) }

        when (event) {
            AutomationEvent.Foreground -> setAppSessionId(UUID.randomUUID().toString())
            AutomationEvent.Background -> setAppSessionId(null)
            else -> {}
        }
    }

    private fun setAppSessionId(id: String?) {
        if (appSessionState.appSessionID == id) { return }

        appSessionState.appSessionID = id
        emit(AutomationEvent.StateChanged(appSessionState))
    }

    private fun startListeningForEvents(): Cancellable {
        val appStateListener = object : ApplicationListener {
            override fun onForeground(milliseconds: Long) {
                emit(AutomationEvent.Foreground)
            }

            override fun onBackground(milliseconds: Long) {
                emit(AutomationEvent.Background)
            }
        }
        activityMonitor.addApplicationListener(appStateListener)

        val analyticsListener = object : AnalyticsListener {
            override fun onScreenTracked(screenName: String) {
                emit(AutomationEvent.ScreenView(screenName))
            }

            override fun onCustomEventAdded(event: CustomEvent) {
                emit(AutomationEvent.CustomEvent(
                    data = event.properties.toJsonValue(),
                    count = event.eventValue?.toDouble()
                ))
            }

            override fun onRegionEventAdded(event: RegionEvent) {
                val trackEvent = when (event.boundaryEvent) {
                    RegionEvent.BOUNDARY_EVENT_ENTER -> AutomationEvent.RegionEnter(event.eventData.toJsonValue())
                    RegionEvent.BOUNDARY_EVENT_EXIT -> AutomationEvent.RegionExit(event.eventData.toJsonValue())
                    else -> null
                }

                trackEvent?.let { emit(it) }
            }

            override fun onFeatureFlagInteractedEventAdded(event: Event) {
                emit(AutomationEvent.FeatureFlagInteracted(event.eventData.toJsonValue()))
            }
        }
        analytics.addAnalyticsListener(analyticsListener)

        return object : Cancellable {
            override fun cancel() {
                activityMonitor.removeApplicationListener(appStateListener)
                analytics.removeAnalyticsListener(analyticsListener)
            }
        }
    }
}
