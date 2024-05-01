/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.html

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.urbanairship.Predicate
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageActivity
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DelegatingDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import com.urbanairship.json.JsonMap
import java.util.UUID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Html display adapter.
 */
internal class HtmlDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.HTMLContent,
    private val assets: AirshipCachedAssetsInterface?,
    private val messageExtras: JsonMap?,
    private val activityMonitor: ActivityMonitor
) : DelegatingDisplayAdapter.Delegate {

    private var continuation: CancellableContinuation<DisplayResult>? = null

    override val activityPredicate: Predicate<Activity>? = null

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
            Intent(context, HtmlActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(InAppMessageActivity.DISPLAY_LISTENER_TOKEN, token)
                .putExtra(InAppMessageActivity.DISPLAY_CONTENT, displayContent)
                .putExtra(InAppMessageActivity.IN_APP_ASSETS, assets)
                .putExtra(HtmlActivity.INTENT_EXTRAS_KEY, messageExtras?.toJsonValue()?.toString())

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine {
                continuation = it
                context.startActivity(intent)
            }
        }
    }

    private fun setDisplayListener(token: String, listener: InAppMessageDisplayListener?) {
        synchronized(listenersMap) {
            if (listener == null) {
                listenersMap.remove(token)
            } else {
                listenersMap[token] = listener
            }
        }
    }

    companion object {
        private val listenersMap = mutableMapOf<String, InAppMessageDisplayListener>()

        fun getListener(token: String): InAppMessageDisplayListener? {
            return synchronized(listenersMap) { listenersMap[token] }
        }
    }
}
