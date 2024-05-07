/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.fullscreen

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.urbanairship.Predicate
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessageActivity
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.adapter.InAppMessageDisplayListener
import com.urbanairship.automation.utils.ActiveTimer
import java.util.UUID
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
    private val activityMonitor: ActivityMonitor
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity>? = null

    private var continuation: Continuation<DisplayResult>? = null

    override suspend fun display(
        context: Context,
        analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {
        val token = UUID.randomUUID().toString()

        val displayListener = InAppMessageDisplayListener(
            analytics = analytics,
            timer = ActiveTimer(activityMonitor),
            onDismiss = {
                setDisplayListener(token, null)
                continuation?.resumeWith(Result.success(it))
            })

        setDisplayListener(token, displayListener)

        val intent =
            Intent(context, FullscreenActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(InAppMessageActivity.DISPLAY_LISTENER_TOKEN, token)
                .putExtra(InAppMessageActivity.DISPLAY_CONTENT, displayContent)
                .putExtra(InAppMessageActivity.IN_APP_ASSETS, assets)

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine {
                continuation = it
                context.startActivity(intent)
            }
        }
    }


    private fun setDisplayListener(token: String, listener: InAppMessageDisplayListener?) {
        synchronized(messageToAnalytics) {
            if (listener == null) {
                messageToAnalytics.remove(token)
            } else {
                messageToAnalytics[token] = listener
            }
        }
    }

    companion object {
        private val messageToAnalytics = mutableMapOf<String, InAppMessageDisplayListener>()
        fun getListener(messageID: String): InAppMessageDisplayListener? {
            return synchronized(messageToAnalytics) { messageToAnalytics[messageID] }
        }
    }
}
