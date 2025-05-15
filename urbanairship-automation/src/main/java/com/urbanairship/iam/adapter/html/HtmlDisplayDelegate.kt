/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.html

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.urbanairship.Predicate
import com.urbanairship.actions.ActionRunner
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.adapter.InAppDisplayArgs
import com.urbanairship.iam.adapter.InAppDisplayArgsLoader
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.json.JsonMap
import com.urbanairship.util.timer.ActiveTimer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Html display adapter.
 */
internal class HtmlDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.HTMLContent,
    private val assets: AirshipCachedAssets?,
    private val messageExtras: JsonMap?,
    private val activityMonitor: ActivityMonitor,
    private val actionRunner: ActionRunner
) : DelegatingDisplayAdapter.Delegate {

    private var continuation: CancellableContinuation<DisplayResult>? = null

    override val activityPredicate: Predicate<Activity>? = null

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

        val displayArgs = InAppDisplayArgs(
            displayContent = displayContent,
            assets = assets,
            displayListener = displayListener,
            extras = messageExtras,
            actionRunner = actionRunner
        )

        val loader = InAppDisplayArgsLoader.newLoader(displayArgs)

        val intent = Intent(context, HtmlActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(InAppMessageActivity.EXTRA_DISPLAY_ARGS_LOADER, loader)

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine {
                continuation = it
                context.startActivity(intent)
            }
        }
    }
}
