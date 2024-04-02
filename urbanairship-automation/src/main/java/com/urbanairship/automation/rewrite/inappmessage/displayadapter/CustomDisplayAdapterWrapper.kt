package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppDisplayEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppResolutionEvent
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.utils.ActiveTimer

/**
 *  Wraps a custom display adapter as a DisplayAdapter
 */
internal class CustomDisplayAdapterWrapper (
    private val adapter: CustomDisplayAdapterInterface
) : DisplayAdapterInterface {

    override suspend fun getIsReady(): Boolean = adapter.getIsReady()
    override suspend fun waitForReady() = adapter.waitForReady()

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
