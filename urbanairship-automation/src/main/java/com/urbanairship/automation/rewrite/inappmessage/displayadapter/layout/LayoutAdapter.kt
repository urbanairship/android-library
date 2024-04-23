/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.AirshipEmbeddedViewManager
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.android.layout.display.DisplayRequest
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.reporting.FormData.BaseForm
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.automation.rewrite.inappmessage.InAppActionUtils
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageWebViewClient
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppButtonTapEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppFormDisplayEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppFormResultEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppGestureEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageActionEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageSwipeEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPageViewEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPagerCompletedEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPagerSummaryEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.InAppPermissionResultEvent
import com.urbanairship.automation.rewrite.inappmessage.analytics.events.PageViewSummary
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.inappmessage.info.InAppMessageButtonInfo
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.emptyJsonMap
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import java.net.MalformedURLException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Airship layout display adapter. */
internal class LayoutAdapter(
    private val displayContent: InAppMessageDisplayContent.AirshipLayoutContent,
    private val assets: AirshipCachedAssetsInterface?,
    private val messageExtras: JsonMap?,
    private val activityMonitor: InAppActivityMonitor
) : DisplayAdapterInterface {

    override fun getIsReady(): Boolean = true
    override suspend fun waitForReady() {}

    private var continuation: CancellableContinuation<DisplayResult>? = null

    @Throws(DisplayException::class, MalformedURLException::class)
    override suspend fun display(
        context: Context,
        analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {

        val displayListener = InAppMessageDisplayListener(
            analytics = analytics,
            timer = ActiveTimer(activityMonitor),
            onDismiss = {
                continuation?.resumeWith(Result.success(it))
            })

        val extras = messageExtras ?: emptyJsonMap()

        val request = Thomas
            .prepareDisplay(displayContent.layout.layoutInfo, extras, EmbeddedViewManager)
            .setListener(Listener(displayListener))
            .setImageCache { url -> assets?.cacheURL(url)?.path }
            .setWebViewClientFactory { InAppMessageWebViewClient(messageExtras) }

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine {
                continuation = it
                request.display(context)
                displayListener.onAppear()
            }
        }
    }

    @VisibleForTesting
    internal class Listener (
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

        override fun onFormResult(formData: BaseForm, state: LayoutData) {
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

            InAppActionUtils.runActions(actions, ActionRunRequestFactory { actionName: String ->
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
}

private class PagerSummary {

    var pagerData: PagerData? = null
    val pageViewSummaries: MutableList<PageViewSummary> = ArrayList<PageViewSummary>()
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
