/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter.layout

import androidx.annotation.VisibleForTesting
import com.urbanairship.UALog
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.reporting.ThomasFormField
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
import com.urbanairship.iam.analytics.events.PageViewSummary
import com.urbanairship.json.JsonValue
import kotlin.math.max

@VisibleForTesting
internal class LayoutListener (
    val analytics: InAppMessageAnalyticsInterface,
    private val timer: ActiveTimerInterface = ManualActiveTimer(),
    private var onDismiss: ((DisplayResult) -> Unit)?
) : ThomasListenerInterface {
    private val completedPagers: MutableSet<String> = HashSet()
    private val pagerSummaryMap: MutableMap<String, PagerSummary> = HashMap()
    private val pagerViewCounts: MutableMap<String, MutableMap<Int, Int>> = HashMap()

    override fun onPageView(pagerData: PagerData, state: LayoutData, displayedAt: Long) {
        // View
        val viewCount = updatePageViewCount(pagerData)
        analytics.recordEvent(
            event = InAppPageViewEvent(pagerData, viewCount),
            layoutContext = state
        )

        // Completed
        if (pagerData.isCompleted && !completedPagers.contains(pagerData.identifier)) {
            completedPagers.add(pagerData.identifier)
            analytics.recordEvent(
                event = InAppPagerCompletedEvent(pagerData),
                layoutContext = state
            )
        }

        // Summary
        pagerSummaryMap
            .getOrPut(pagerData.identifier) { PagerSummary() }
            .updatePagerData(pagerData, displayedAt)
    }

    override fun onPageSwipe(
        pagerData: PagerData,
        toPageIndex: Int,
        toPageId: String,
        fromPageIndex: Int,
        fromPageId: String,
        state: LayoutData
    ) {
        analytics.recordEvent(
            InAppPageSwipeEvent(
                from = PagerData(
                    pagerData.identifier,
                    fromPageIndex,
                    fromPageId,
                    pagerViewCounts[pagerData.identifier]?.get(fromPageIndex) ?: 0,
                    pagerData.isCompleted),
                to = PagerData(
                    pagerData.identifier,
                    toPageIndex,
                    toPageId,
                    pagerViewCounts[pagerData.identifier]?.get(toPageIndex) ?: 0,
                    pagerData.isCompleted)
            ),
            layoutContext = state
        )
    }

    override fun onButtonTap(
        buttonId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        analytics.recordEvent(
            event = InAppButtonTapEvent(buttonId, reportingMetadata),
            layoutContext = state
        )
    }

    override fun onDismiss(
        buttonId: String,
        buttonDescription: String?,
        cancel: Boolean,
        displayTime: Long,
        state: LayoutData
    ) {
        sendPageSummaryEvents(layoutData = state, displayTime)
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.buttonTap(
                    identifier = buttonId,
                    description = buttonDescription ?: "",
                    displayTime = time),
                layoutContext = state
            )

            if (cancel) {
                DisplayResult.CANCEL
            } else {
                DisplayResult.FINISHED
            }
        }
    }

    override fun onDismiss(displayTime: Long) {
        sendPageSummaryEvents(null, displayTime)
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.userDismissed(time),
                layoutContext = null
            )
            DisplayResult.FINISHED
        }
    }

    override fun onFormResult(thomasFormField: ThomasFormField.BaseForm, state: LayoutData) {
        analytics.recordEvent(
            event = InAppFormResultEvent(thomasFormField.toJsonValue()),
            layoutContext = state
        )
    }

    override fun onFormDisplay(formInfo: FormInfo, state: LayoutData) {
        analytics.recordEvent(
            event = InAppFormDisplayEvent(formInfo),
            layoutContext = state
        )
    }

    override fun onPagerGesture(
        gestureId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        analytics.recordEvent(
            event = InAppGestureEvent(gestureId, reportingMetadata),
            layoutContext = state
        )
    }

    override fun onPagerAutomatedAction(
        actionId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        analytics.recordEvent(
            event = InAppPageActionEvent(actionId, reportingMetadata),
            layoutContext = state
        )
    }

    override fun onVisibilityChanged(isVisible: Boolean, isForegrounded: Boolean) {
        if (isVisible && isForegrounded) {
            analytics.recordEvent(InAppDisplayEvent(), null)
            timer.start()
        } else {
            timer.stop()
        }
    }

    override fun onTimedOut(state: LayoutData?) {
        tryDismiss { time ->
            analytics.recordEvent(
                InAppResolutionEvent.timedOut(time),
                state
            )
            DisplayResult.FINISHED
        }
    }


    /**
     * Updates the pager page view count map.
     *
     * @param data PagerData from the page view event.
     * @return the updated viewed count for the current page index.
     */
    private fun updatePageViewCount(data: PagerData): Int {
        if (!pagerViewCounts.containsKey(data.identifier)) {
            pagerViewCounts[data.identifier] = HashMap()
        }
        val pageViews = pagerViewCounts[data.identifier]
        if (pageViews != null && !pageViews.containsKey(data.index)) {
            pageViews[data.index] = 0
        }
        val count = (pageViews?.get(data.index) ?: 0) + 1
        pageViews?.set(data.index, count)
        return count
    }

    private fun sendPageSummaryEvents(layoutData: LayoutData?, displayTime: Long) {
        pagerSummaryMap
            .values
            .forEach {
                it.pageFinished(displayTime)
                val data = it.pagerData ?: return@forEach

                analytics.recordEvent(
                    event = InAppPagerSummaryEvent(data, it.pageViewSummaries),
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
        val result = block.invoke(timer.time)
        dismiss(result)
        onDismiss = null
    }
}

private class PagerSummary {

    var pagerData: PagerData? = null
    val pageViewSummaries: MutableList<PageViewSummary> = mutableListOf()
    var pageUpdateTime: Long = 0

    fun updatePagerData(data: PagerData, updateTime: Long) {
        pageFinished(updateTime)
        pagerData = data
        pageUpdateTime = updateTime
    }

    fun pageFinished(updateTime: Long) {
        val data = pagerData ?: return

        val duration = updateTime - pageUpdateTime
        val summary = PageViewSummary(data.pageId, data.index, duration)
        pageViewSummaries.add(summary)
    }
}
