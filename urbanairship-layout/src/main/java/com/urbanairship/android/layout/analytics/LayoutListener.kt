package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.analytics.events.LayoutButtonTapEvent
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutFormDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutFormResultEvent
import com.urbanairship.android.layout.analytics.events.LayoutGestureEvent
import com.urbanairship.android.layout.analytics.events.LayoutPageActionEvent
import com.urbanairship.android.layout.analytics.events.LayoutPageSwipeEvent
import com.urbanairship.android.layout.analytics.events.LayoutPageViewEvent
import com.urbanairship.android.layout.analytics.events.LayoutPagerCompletedEvent
import com.urbanairship.android.layout.analytics.events.LayoutPagerSummaryEvent
import com.urbanairship.android.layout.analytics.events.LayoutResolutionEvent
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.json.JsonSerializable

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class DisplayResult {
    CANCEL, FINISHED
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@VisibleForTesting
public class LayoutListener (
    private val analytics: LayoutMessageAnalyticsInterface,
    private var onDismiss: ((DisplayResult) -> Unit)?
) : ThomasListenerInterface {

    override fun onReportingEvent(event: ReportingEvent) {
        when (event) {
            is ReportingEvent.ButtonTap -> {
                analytics.recordEvent(
                    event = LayoutButtonTapEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.Dismiss -> {
                when (val source = event.data) {
                    is ReportingEvent.DismissData.ButtonTapped -> {
                        analytics.recordEvent(
                            event = LayoutResolutionEvent.buttonTap(
                                identifier = source.identifier,
                                description = source.description,
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )
                    }

                    ReportingEvent.DismissData.TimedOut -> {
                        analytics.recordEvent(
                            event = LayoutResolutionEvent.timedOut(
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )
                    }

                    ReportingEvent.DismissData.UserDismissed -> {
                        analytics.recordEvent(
                            event = LayoutResolutionEvent.userDismissed(
                                displayTime = event.displayTime
                            ), layoutContext = event.context
                        )
                    }
                }
            }
            is ReportingEvent.FormDisplay -> {
                analytics.recordEvent(
                    event = LayoutFormDisplayEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.FormResult -> {
                analytics.recordEvent(
                    event = LayoutFormResultEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.Gesture -> {
                analytics.recordEvent(
                    event = LayoutGestureEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageAction -> {
                analytics.recordEvent(
                    event = LayoutPageActionEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PagerSummary -> {
                analytics.recordEvent(
                    event = LayoutPagerSummaryEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageSwipe -> {
                analytics.recordEvent(
                    event = LayoutPageSwipeEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PageView -> {
                analytics.recordEvent(
                    event = LayoutPageViewEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.PagerComplete -> {
                analytics.recordEvent(
                    event = LayoutPagerCompletedEvent(event.data),
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
        this.onDismiss = null
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        if (isVisible && isForegrounded) {
            analytics.recordEvent(InAppDisplayEvent(), null)
        }
    }

    override fun onStateChanged(state: JsonSerializable) { }
}
