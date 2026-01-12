/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import com.urbanairship.UALog
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.util.timer.Timer
import kotlin.time.Duration

internal class InAppMessageDisplayListener(
    val analytics: InAppMessageAnalyticsInterface,
    private val timer: Timer,
    private var onDismiss: ((DisplayResult) -> Unit)?,
) {
    fun onAppear() {
        timer.start()
        analytics.recordEvent(InAppDisplayEvent(), null)
    }

    val isDisplaying: Boolean
        get() { return onDismiss != null }

    fun onPause() {
        timer.stop()
    }

    fun onResume() {
        timer.start()
    }

    fun onButtonDismissed(info: InAppMessageButtonInfo) {
        tryDismiss { displayTime ->
            analytics.recordEvent(
                LayoutResolutionEvent.buttonTap(
                    identifier = info.identifier,
                    description = info.label.text,
                    displayTime = displayTime
                ),
                layoutContext = null
            )

            if (info.behavior == InAppMessageButtonInfo.Behavior.CANCEL) DisplayResult.CANCEL
            else DisplayResult.FINISHED
        }
    }

    fun onTimedOut() {
        tryDismiss { displayTime ->
            analytics.recordEvent(
                LayoutResolutionEvent.timedOut(
                    displayTime = displayTime
                ), layoutContext = null
            )
            DisplayResult.FINISHED
        }
    }

    fun onUserDismissed() {
        tryDismiss { displayTime ->
            analytics.recordEvent(
                LayoutResolutionEvent.userDismissed(
                    displayTime = displayTime
                ), layoutContext = null
            )
            DisplayResult.FINISHED
        }
    }

    fun onMessageTapDismissed() {
        tryDismiss { displayTime ->
            analytics.recordEvent(
                LayoutResolutionEvent.messageTap(
                    displayTime = displayTime
                ),
                layoutContext = null
            )
            DisplayResult.FINISHED
        }
    }

    private fun tryDismiss(block: (Duration) -> DisplayResult) {
        val dismiss = this.onDismiss
        if (dismiss == null) {
            UALog.e { "Dismissed already called!" }
            return
        }

        timer.stop()
        val result = block.invoke(timer.time)
        dismiss(result)
        onDismiss = null
    }
}
