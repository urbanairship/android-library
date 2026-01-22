/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.fullscreen

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.urbanairship.Predicate
import com.urbanairship.actions.ActionRunner
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.InAppDisplayArgs
import com.urbanairship.iam.adapter.InAppDisplayArgsLoader
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.util.timer.ActiveTimer
import kotlin.coroutines.Continuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Full screen adapter.
 */
internal class FullscreenDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.FullscreenContent,
    private val assets: AirshipCachedAssets?,
    private val activityMonitor: ActivityMonitor,
    private val actionRunner: ActionRunner
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity>? = null

    private var continuation: Continuation<DisplayResult>? = null

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
            actionRunner = actionRunner
        )

        val loader = InAppDisplayArgsLoader.newLoader(displayArgs)

        val intent = Intent(context, FullscreenActivity::class.java)
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
