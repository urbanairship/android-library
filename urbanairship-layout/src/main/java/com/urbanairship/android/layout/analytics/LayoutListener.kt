package com.urbanairship.android.layout.analytics

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.analytics.events.LayoutButtonTapEvent
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.InAppFormDisplayEvent
import com.urbanairship.android.layout.analytics.events.InAppFormResultEvent
import com.urbanairship.android.layout.analytics.events.InAppGestureEvent
import com.urbanairship.android.layout.analytics.events.InAppPageActionEvent
import com.urbanairship.android.layout.analytics.events.InAppPageSwipeEvent
import com.urbanairship.android.layout.analytics.events.LayoutPageViewEvent
import com.urbanairship.android.layout.analytics.events.InAppPagerCompletedEvent
import com.urbanairship.android.layout.analytics.events.InAppPagerSummaryEvent
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
                    event = LayoutPageViewEvent(event.data),
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
        this.onDismiss = null
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        if (isVisible && isForegrounded) {
            analytics.recordEvent(InAppDisplayEvent(), null)
        }
    }

    override fun onStateChanged(state: JsonSerializable) { }
}
