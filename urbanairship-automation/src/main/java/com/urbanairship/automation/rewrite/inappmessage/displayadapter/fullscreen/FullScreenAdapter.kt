/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen

import android.content.Context
import android.content.Intent
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageActivity
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Full screen adapter.
 */
internal class FullScreenAdapter(
    private val displayContent: InAppMessageDisplayContent.FullscreenContent,
    private val assets: AirshipCachedAssetsInterface?,
    private val activityMonitor: InAppActivityMonitor
) : DisplayAdapterInterface {

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
            Intent(context, FullScreenActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    override fun getIsReady(): Boolean = true
    override suspend fun waitForReady() {}

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
