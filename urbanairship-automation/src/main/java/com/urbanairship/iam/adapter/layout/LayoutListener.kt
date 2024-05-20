package com.urbanairship.iam.adapter.layout

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.iam.analytics.events.InAppButtonTapEvent
import com.urbanairship.iam.analytics.events.InAppFormDisplayEvent
import com.urbanairship.iam.analytics.events.InAppFormResultEvent
import com.urbanairship.iam.analytics.events.InAppGestureEvent
import com.urbanairship.iam.analytics.events.InAppPageActionEvent
import com.urbanairship.iam.analytics.events.InAppPageSwipeEvent
import com.urbanairship.iam.analytics.events.InAppPageViewEvent
import com.urbanairship.iam.analytics.events.InAppPagerCompletedEvent
import com.urbanairship.iam.analytics.events.InAppPagerSummaryEvent
import com.urbanairship.iam.analytics.events.InAppPermissionResultEvent
import com.urbanairship.iam.analytics.events.PageViewSummary
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus

@VisibleForTesting
internal class LayoutListener (
    val displayListener: InAppMessageDisplayListener
) : ThomasListener {

    private val completedPagers: MutableSet<String> = HashSet()
    private val pagerSummaryMap: MutableMap<String, PagerSummary> = HashMap()
    private val pagerViewCounts: MutableMap<String, MutableMap<Int, Int>> = HashMap()

    override fun onPageView(pagerData: PagerData, state: LayoutData, displayedAt: Long) {
        // View
        val viewCount = updatePageViewCount(pagerData)
        displayListener.analytics.recordEvent(
            event = InAppPageViewEvent(pagerData, viewCount),
            layoutContext = state
        )

        // Completed
        if (pagerData.isCompleted && !completedPagers.contains(pagerData.identifier)) {
            completedPagers.add(pagerData.identifier)
            displayListener.analytics.recordEvent(
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
        displayListener.analytics.recordEvent(
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
        displayListener.analytics.recordEvent(
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
        val behavior = if (cancel) InAppMessageButtonInfo.Behavior.CANCEL
        else InAppMessageButtonInfo.Behavior.DISMISS
        displayListener.onButtonDismissed(buttonId, buttonDescription ?: "", behavior, state)
    }

    override fun onDismiss(displayTime: Long) {
        sendPageSummaryEvents(null, displayTime)
        displayListener.onUserDismissed()
    }

    override fun onFormResult(formData: FormData.BaseForm, state: LayoutData) {
        displayListener.analytics.recordEvent(
            event = InAppFormResultEvent(formData.toJsonValue()),
            layoutContext = state
        )
    }

    override fun onFormDisplay(formInfo: FormInfo, state: LayoutData) {
        displayListener.analytics.recordEvent(
            event = InAppFormDisplayEvent(formInfo),
            layoutContext = state
        )
    }

    override fun onRunActions(actions: Map<String, JsonValue>, state: LayoutData) {

        val permissionResultReceiver = object : PermissionResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onResult(
                permission: Permission,
                before: PermissionStatus,
                after: PermissionStatus
            ) {
                onPromptPermissionResult(permission, before, after, state)
            }
        }

        com.urbanairship.iam.InAppActionUtils.runActions(JsonMap(actions), ActionRunRequestFactory { actionName: String ->
            val bundle = Bundle()
            bundle.putParcelable(
                PromptPermissionAction.RECEIVER_METADATA,
                permissionResultReceiver
            )
            ActionRunRequest.createRequest(actionName).setMetadata(bundle)
        })
    }

    override fun onPagerGesture(
        gestureId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        displayListener.analytics.recordEvent(
            event = InAppGestureEvent(gestureId, reportingMetadata),
            layoutContext = state
        )
    }

    override fun onPagerAutomatedAction(
        actionId: String,
        reportingMetadata: JsonValue?,
        state: LayoutData
    ) {
        displayListener.analytics.recordEvent(
            event = InAppPageActionEvent(actionId, reportingMetadata),
            layoutContext = state
        )
    }

    internal fun onPromptPermissionResult(
        permission: Permission,
        before: PermissionStatus,
        after: PermissionStatus,
        layoutContext: LayoutData
    ) {
        displayListener.analytics.recordEvent(
            event = InAppPermissionResultEvent(permission, before, after),
            layoutContext = layoutContext
        )
    }

    /**
     * Updates the pager page view count map.
     *
     * @param data PagerData from the page view event.
     * @return the updated viewed count for the current page index.
     */
    private fun updatePageViewCount(data: PagerData): Int {
        if (!pagerViewCounts.containsKey(data.identifier)) {
            pagerViewCounts[data.identifier] = HashMap(data.count)
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

                displayListener.analytics.recordEvent(
                    event = InAppPagerSummaryEvent(data, it.pageViewSummaries),
                    layoutContext = layoutData
                )
            }
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
