package com.urbanairship.automation.rewrite

import androidx.annotation.RestrictTo
import com.urbanairship.ApplicationMetrics
import com.urbanairship.analytics.Analytics
import com.urbanairship.analytics.AnalyticsListener
import com.urbanairship.analytics.CustomEvent
import com.urbanairship.analytics.Event
import com.urbanairship.analytics.location.RegionEvent
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.ApplicationListener
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class TriggerableState(
    var appSessionID: String? = null,
    var versionUpdated: String? = null
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
    internal data class ScreenView(val name: String?): AutomationEvent()
    internal data class StateChanged(val state: TriggerableState) : AutomationEvent()
    internal data class RegionEnter(val data: JsonValue) : AutomationEvent()
    internal data class RegionExit(val data: JsonValue) : AutomationEvent()
    internal data class CustomEvent(val data: JsonValue, val count: Double?) : AutomationEvent()
    internal data class FeatureFlagInteracted(val data: JsonValue) : AutomationEvent()

    internal fun reportPayload(): JsonValue? {
        return when(this) {
            Foreground, Background, AppInit, is StateChanged -> null
            is ScreenView -> JsonValue.wrap(name)
            is CustomEvent -> data
            is FeatureFlagInteracted -> data
            is RegionEnter -> data
            is RegionExit -> data
        }
    }

    internal fun isStateEvent(): Boolean {
        return when(this) {
            is StateChanged -> true
            else -> false
        }
    }
}

private interface Cancellable {
    fun cancel()
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AutomationEventFeed(
    private val applicationMetrics: ApplicationMetrics,
    private val activityMonitor: ActivityMonitor,
    private val analytics: Analytics
) {
    private val stream = MutableSharedFlow<AutomationEvent>()
    private val appSessionState = TriggerableState()
    private var subscription: Cancellable? = null
    private var isFirstAttach = false

    internal val feed: Flow<AutomationEvent> = stream

    internal fun attach() {
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

    internal fun detach() {
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
