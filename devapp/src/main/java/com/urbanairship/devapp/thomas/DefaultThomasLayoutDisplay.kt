package com.urbanairship.devapp.thomas

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.actions.Action
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.actions.run
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.json.jsonMapOf

internal class DefaultThomasLayoutDisplay private constructor() {

    fun display(context: Context, info: LayoutInfo) {
        Thomas.prepareDisplay(
            payload = info,
            priority = 0,
            extras = emptyJsonMap(),
            activityMonitor = GlobalActivityMonitor.shared(context),
            listener = thomasListener,
            actionRunner = actionRunner,
            embeddedViewManager = EmbeddedViewManager
        ).display(context)
    }

    private val actionRunner: ThomasActionRunner = object: ThomasActionRunner {
        override fun run(actions: Map<String, JsonValue>, state: LayoutData) {
            DefaultActionRunner.run(actions, Action.Situation.AUTOMATION)
        }
    }

    private val thomasListener = object : ThomasListenerInterface {
        private val events = mutableListOf<String>()

        override fun onStateChanged(state: JsonSerializable) { }

        override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
            "onVisibilityChanged(isVisible: $isVisible, isForegrounded: $isForegrounded)".let {
                events.add(it)
                UALog.d(it)
            }
        }

        override fun onDismiss(cancel: Boolean) {
            "onDismiss(cancel: $cancel".let {
                events.add(it)
                UALog.d(it)
            }
        }

        @Throws(JsonException::class)
        override fun onReportingEvent(event: ReportingEvent) {
            when (event) {
                is ReportingEvent.ButtonTap -> logEvent("button_tap", event.data, event.context)

                is ReportingEvent.FormDisplay -> logEvent("form_display", event.data, event.context)

                is ReportingEvent.FormResult -> logEvent("form_result", event.data, event.context)

                is ReportingEvent.Gesture -> logEvent("gesture", event.data, event.context)

                is ReportingEvent.PageAction -> logEvent("page_action", event.data, event.context)

                is ReportingEvent.PageSwipe -> logEvent("page_swipe", event.data, event.context)

                is ReportingEvent.PageView -> logEvent("page_view", event.data, event.context)

                is ReportingEvent.PagerComplete -> logEvent("pager_complete", event.data, event.context)

                is ReportingEvent.PagerSummary -> logEvent("pager_summary", event.data, event.context)

                is ReportingEvent.Dismiss -> when (val eventData = event.data) {
                    is ReportingEvent.DismissData.ButtonTapped -> {
                        logEvent(
                            name = "dismiss",
                            event = jsonMapOf(
                                "button_identifier" to eventData.identifier,
                                "button_description" to eventData.description
                            ),
                            context = event.context
                        )
                    }

                    ReportingEvent.DismissData.TimedOut ->
                        logEvent("dismiss", JsonValue.wrap("timedOut"), event.context)

                    ReportingEvent.DismissData.UserDismissed ->
                        logEvent("dismiss", JsonValue.wrap("userDismissed"), event.context)
                }.also { dumpEvents() }
            }
        }

        private fun logEvent(name: String, event: JsonSerializable, context: LayoutData) {
            "$name - event: ${event.toJsonValue()}, context: ${context.toJsonValue()}".let {
                events.add(it)
                UALog.d(it)
            }
        }

        private fun dumpEvents() {
            UALog.d("\n")
            UALog.d("---- LAYOUT EVENTS ----")
            events.forEachIndexed { index, s -> UALog.d("$index: $s") }
            UALog.d("-----------------------")
            UALog.d("\n")
            events.clear()
        }
    }

    companion object {
        val shared: DefaultThomasLayoutDisplay = DefaultThomasLayoutDisplay()
    }
}
