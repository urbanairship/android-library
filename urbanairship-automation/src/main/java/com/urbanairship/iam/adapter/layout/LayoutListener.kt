/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter.layout

import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.analytics.events.InAppButtonTapEvent
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppFormDisplayEvent
import com.urbanairship.iam.analytics.events.InAppFormResultEvent
import com.urbanairship.iam.analytics.events.InAppGestureEvent
import com.urbanairship.iam.analytics.events.InAppPageActionEvent
import com.urbanairship.iam.analytics.events.InAppPageSwipeEvent
import com.urbanairship.iam.analytics.events.InAppPageViewEvent
import com.urbanairship.iam.analytics.events.InAppPagerCompletedEvent
import com.urbanairship.iam.analytics.events.InAppPagerSummaryEvent
import com.urbanairship.iam.analytics.events.InAppResolutionEvent
import com.urbanairship.json.JsonSerializable

@VisibleForTesting
internal class LayoutListener (
    val analytics: InAppMessageAnalyticsInterface,
    private var onDismiss: ((DisplayResult) -> Unit)?
) : ThomasListenerInterface {

    override fun onReportingEvent(event: ReportingEvent) {
        when (event) {
            is ReportingEvent.ButtonTap -> {
                analytics.recordEvent(
                    event = InAppButtonTapEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.Dismiss -> {
                when (val source = event.data) {
                    is ReportingEvent.DismissData.ButtonTapped -> {
                        analytics.recordEvent(
                            event = InAppResolutionEvent.buttonTap(
                                identifier = source.identifier,
                                description = source.description,
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )

                        if (source.cancel) DisplayResult.CANCEL else DisplayResult.FINISHED
                    }

                    ReportingEvent.DismissData.TimedOut -> {
                        analytics.recordEvent(
                            event = InAppResolutionEvent.timedOut(
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )
                        DisplayResult.FINISHED
                    }

                    ReportingEvent.DismissData.UserDismissed -> {
                        analytics.recordEvent(
                            event = InAppResolutionEvent.userDismissed(
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )
                        DisplayResult.FINISHED
                    }
                }
            }
            is ReportingEvent.FormDisplay -> {
                analytics.recordEvent(
                    event = InAppFormDisplayEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.FormResult -> {
                analytics.recordEvent(
                    event = InAppFormResultEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.Gesture -> {
                analytics.recordEvent(
                    event = InAppGestureEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageAction -> {
                analytics.recordEvent(
                    event = InAppPageActionEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PagerSummary -> {
                analytics.recordEvent(
                    event = InAppPagerSummaryEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageSwipe -> {
                analytics.recordEvent(
                    event = InAppPageSwipeEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageView -> {
                analytics.recordEvent(
                    event = InAppPageViewEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PagerComplete -> {
                analytics.recordEvent(
                    event = InAppPagerCompletedEvent(event.data),
                    layoutContext = event.context
                )
            }
        }
    }

    override fun onDismiss(cancel: Boolean) {
        val dismiss = this.onDismiss
        if (dismiss == null) {
            UALog.e { "Dismissed already called!" }
            return
        }

        val result = if (cancel) DisplayResult.CANCEL else DisplayResult.FINISHED
        dismiss(result)
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        if (isVisible && isForegrounded) {
            analytics.recordEvent(InAppDisplayEvent(), null)
        }
    }

    override fun onStateChanged(state: JsonSerializable) { }
}
