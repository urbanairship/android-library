package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.LayoutData

internal interface Reporter {
    fun report(event: ReportingEvent)
    fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean)
}

internal class ExternalReporter(val listener: ThomasListenerInterface) : Reporter {

    override fun report(event: ReportingEvent) {
        listener.onReportingEvent(event)
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        listener.onVisibilityChanged(isVisible, isForegrounded)
    }
}
