/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import com.urbanairship.UALog
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.automation.utils.ActiveTimer
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo

internal class InAppMessageDisplayListener(
    val analytics: InAppMessageAnalyticsInterface,
    private val timer: ActiveTimer,
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

    fun onButtonDismissed(
        identifier: String,
        description: String,
        behavior: InAppMessageButtonInfo.Behavior,
        layoutContext: LayoutData?
    ) {
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.buttonTap(
                identifier = identifier,
                description = description,
                displayTime = time),
                layoutContext = layoutContext
            )

            if (behavior == InAppMessageButtonInfo.Behavior.CANCEL) DisplayResult.CANCEL
            else DisplayResult.FINISHED
        }
    }

    fun onButtonDismissed(info: InAppMessageButtonInfo) {
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.buttonTap(
                identifier = info.identifier,
                description = info.label.text,
                displayTime = time),
                layoutContext = null
            )

            if (info.behavior == InAppMessageButtonInfo.Behavior.CANCEL) DisplayResult.CANCEL
            else DisplayResult.FINISHED
        }
    }

    fun onTimedOut() {
        tryDismiss {
            analytics.recordEvent(InAppResolutionEvent.timedOut(it), layoutContext = null)
            DisplayResult.FINISHED
        }
    }

    fun onUserDismissed() {
        tryDismiss {
            analytics.recordEvent(InAppResolutionEvent.userDismissed(it), layoutContext = null)
            DisplayResult.FINISHED
        }
    }

    fun onMessageTapDismissed() {
        tryDismiss {
            analytics.recordEvent(InAppResolutionEvent.messageTap(it), layoutContext = null)
            DisplayResult.FINISHED
        }
    }

    private fun tryDismiss(block: (Long) -> DisplayResult) {
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
