package com.urbanairship.iam.adapter

import android.content.Context
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.automation.utils.ActiveTimer
import kotlinx.coroutines.flow.StateFlow

/**
 *  Wraps a custom display adapter as a DisplayAdapter
 */
internal class CustomDisplayAdapterWrapper (
    val adapter: CustomDisplayAdapter
) : DisplayAdapter {

    override val isReady: StateFlow<Boolean> = adapter.isReady

    override suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult {
        analytics.recordEvent(InAppDisplayEvent(), layoutContext = null)
        analytics.recordImpression()

        val timer = ActiveTimer(GlobalActivityMonitor.shared(context))
        timer.start()
        val result = adapter.display(context)
        timer.stop()

        return when(result) {
            is CustomDisplayResolution.ButtonTap -> {
                analytics.recordEvent(
                    InAppResolutionEvent.buttonTap(
                        identifier = result.info.identifier,
                        description = result.info.label.text,
                        displayTime = timer.time
                    ),
                    layoutContext = null
                )
                if (result.info.behavior == InAppMessageButtonInfo.Behavior.CANCEL) {
                    DisplayResult.CANCEL
                } else {
                    DisplayResult.FINISHED
                }
            }
            is CustomDisplayResolution.MessageTap -> {
                analytics.recordEvent(
                    event = InAppResolutionEvent.messageTap(timer.time),
                    layoutContext = null)
                DisplayResult.FINISHED
            }
            is CustomDisplayResolution.TimedOut -> {
                analytics.recordEvent(
                    event = InAppResolutionEvent.timedOut(timer.time),
                    layoutContext = null)
                DisplayResult.FINISHED
            }
            is CustomDisplayResolution.UserDismissed -> {
                analytics.recordEvent(
                    event = InAppResolutionEvent.userDismissed(timer.time),
                    layoutContext = null)
                DisplayResult.FINISHED
            }
        }
    }
}
