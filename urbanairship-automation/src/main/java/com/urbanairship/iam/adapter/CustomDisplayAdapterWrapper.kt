/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.content.Context
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.util.timer.ActiveTimer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 *  Wraps a custom display adapter as a DisplayAdapter
 */
internal class CustomDisplayAdapterWrapper (
    val adapter: CustomDisplayAdapter
) : DisplayAdapter {

    override val isReady: StateFlow<Boolean>
        get() {
            return when(adapter) {
                is CustomDisplayAdapter.CallbackAdapter -> MutableStateFlow(true).asStateFlow()
                is CustomDisplayAdapter.SuspendingAdapter -> adapter.isReady
            }
        }

    override suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult {
        analytics.recordEvent(InAppDisplayEvent(), layoutContext = null)

        val timer = ActiveTimer(GlobalActivityMonitor.shared(context))
        timer.start()
        val result: CustomDisplayResolution =  when(adapter) {
            is CustomDisplayAdapter.CallbackAdapter -> {
                suspendCoroutine { continuation ->
                    val callback = DisplayFinishedCallback.newCallback {
                        continuation.resume(it)
                    }
                    adapter.display(context, callback)
                }
            }
            is CustomDisplayAdapter.SuspendingAdapter -> adapter.display(context)
        }
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
                    event = InAppResolutionEvent.messageTap(
                        displayTime = timer.time
                    ),
                    layoutContext = null)
                DisplayResult.FINISHED
            }
            is CustomDisplayResolution.TimedOut -> {
                analytics.recordEvent(
                    event = InAppResolutionEvent.timedOut(
                        displayTime = timer.time
                    ),
                    layoutContext = null)
                DisplayResult.FINISHED
            }
            is CustomDisplayResolution.UserDismissed -> {
                analytics.recordEvent(
                    event = InAppResolutionEvent.userDismissed(
                        displayTime = timer.time
                    ),
                    layoutContext = null
                )
                DisplayResult.FINISHED
            }
        }
    }
}
