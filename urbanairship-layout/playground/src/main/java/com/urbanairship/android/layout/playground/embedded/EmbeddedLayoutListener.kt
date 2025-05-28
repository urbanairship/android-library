package com.urbanairship.android.layout.playground.embedded

import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

internal class EmbeddedLayoutListener(
    private val layoutName: String
) : ThomasListenerInterface {

    override fun onStateChanged(state: JsonSerializable) { }

    override fun onDismiss(cancel: Boolean) {
        UALog.d { "$layoutName - onDismiss(cancel: $cancel)" }
    }

    override fun onReportingEvent(event: ReportingEvent) {
        UALog.d { "$layoutName - $event" }
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        UALog.d { "$layoutName - onVisibilityChanged(isVisible: $isVisible, isForegrounded: $isForegrounded)" }
    }
}
