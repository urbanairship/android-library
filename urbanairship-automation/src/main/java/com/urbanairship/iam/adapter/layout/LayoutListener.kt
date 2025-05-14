/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter.layout

import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.automation.utils.ActiveTimerInterface
import com.urbanairship.automation.utils.ManualActiveTimer
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
    private val pagersViewTracker: PagersViewTracker = PagersViewTracker(),
    private val timer: ActiveTimerInterface = ManualActiveTimer(),
    private var onDismiss: ((DisplayResult) -> Unit)?
) : ThomasListenerInterface {
    private val completedPagers: MutableSet<String> = mutableSetOf()

    override fun onReportingEvent(event: ReportingEvent) {
        when (event) {
            is ReportingEvent.ButtonTap -> {
                analytics.recordEvent(
                    event = InAppButtonTapEvent(event.data),
                    layoutContext = event.context
                )
            }
            is ReportingEvent.Dismiss -> {
                tryDismiss { displayTime ->
                    when(val source = event.data) {
                        is ReportingEvent.DismissData.ButtonTapped -> {
                            analytics.recordEvent(
                                event = InAppResolutionEvent.buttonTap(
                                    identifier = source.identifier,
                                    description = source.description,
                                    displayTime = displayTime
                                ),
                                layoutContext = event.context
                            )

                            sendPageSummaryEvents(event.context)

                            if (source.cancel) DisplayResult.CANCEL else DisplayResult.FINISHED
                        }
                        ReportingEvent.DismissData.TimedOut -> {
                            analytics.recordEvent(
                                event = InAppResolutionEvent.timedOut(displayTime),
                                layoutContext = event.context
                            )
                            DisplayResult.FINISHED
                        }
                        ReportingEvent.DismissData.UserDismissed -> {
                            analytics.recordEvent(
                                event = InAppResolutionEvent.userDismissed(displayTime),
                                layoutContext = event.context
                            )

                            sendPageSummaryEvents(event.context)

                            DisplayResult.FINISHED
                        }
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

                pagersViewTracker.onPageView(event.data, timer.time)
            }
            is ReportingEvent.PagerComplete -> {
                if (completedPagers.contains(event.data.identifier)) {
                    return
                }

                completedPagers.add(event.data.identifier)
                analytics.recordEvent(
                    event = InAppPagerCompletedEvent(event.data),
                    layoutContext = event.context
                )
            }
        }
    }

    override fun onDismiss(cancel: Boolean) {
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.userDismissed(time),
                layoutContext = null
            )

            if (cancel) DisplayResult.CANCEL else DisplayResult.FINISHED
        }
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        if (isVisible && isForegrounded) {
            analytics.recordEvent(InAppDisplayEvent(), null)
            timer.start()
        } else {
            timer.stop()
        }
    }

    override fun onStateChanged(state: JsonSerializable) { }


    /**
     * Updates the pager page view count map.
     *
     * @param data PagerData from the page view event.
     * @return the updated viewed count for the current page index.
     */


    private fun sendPageSummaryEvents(layoutData: LayoutData) {
        pagersViewTracker.generateSummaryEvents().forEach { data ->
            analytics.recordEvent(
                event = InAppPagerSummaryEvent(data),
                layoutContext = layoutData
            )
        }
    }

    private fun tryDismiss(block: (Long) -> DisplayResult) {
        val dismiss = this.onDismiss
        if (dismiss == null) {
            UALog.e { "Dismissed already called!" }
            return
        }

        timer.stop()
        pagersViewTracker.stopAll(timer.time)

        val result = block.invoke(timer.time)
        dismiss(result)
        onDismiss = null
    }
}
