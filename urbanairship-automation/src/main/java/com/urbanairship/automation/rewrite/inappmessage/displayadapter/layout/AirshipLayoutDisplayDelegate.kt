/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.urbanairship.Predicate
import com.urbanairship.actions.ActionRunRequest
import com.urbanairship.actions.ActionRunRequestFactory
import com.urbanairship.actions.PermissionResultReceiver
import com.urbanairship.actions.PromptPermissionAction
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.ThomasListener
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.android.layout.reporting.FormData.BaseForm
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.app.ActivityMonitor
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
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DelegatingDisplayAdapter
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
internal class AirshipLayoutDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.AirshipLayoutContent,
    private val assets: AirshipCachedAssetsInterface?,
    private val messageExtras: JsonMap?,
    private val activityMonitor: ActivityMonitor
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity>? = null

    private var continuation: CancellableContinuation<DisplayResult>? = null

    @Throws(DisplayException::class, MalformedURLException::class)
    override suspend fun display(
        context: Context, analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {

        val displayListener = InAppMessageDisplayListener(analytics = analytics,
            timer = ActiveTimer(activityMonitor),
            onDismiss = {
                continuation?.resumeWith(Result.success(it))
            })

        val extras = messageExtras ?: emptyJsonMap()

        val request =
            Thomas.prepareDisplay(displayContent.layout.layoutInfo, extras, EmbeddedViewManager)
                .setListener(LayoutListener(displayListener))
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
}
