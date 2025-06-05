package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent

internal interface Reporter {
    fun report(event: ReportingEvent)
    fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean)
}

internal class ExternalReporter(val listener: ThomasListenerInterface) : Reporter {

    override fun report(event: ReportingEvent) {
        listener.onReportingEvent(event)
        when (event) {
            is ReportingEvent.Dismiss -> {
                when (event.data) {
                    is ReportingEvent.DismissData.ButtonTapped -> with(event.data) {
                        listener.onDismiss(cancel)
                    }
                    ReportingEvent.DismissData.TimedOut -> listener.onDismiss(false)
                    ReportingEvent.DismissData.UserDismissed -> listener.onDismiss(true)
                }
            }
            else -> {}
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        listener.onVisibilityChanged(isVisible, isForegrounded)
    }
}
