/* Copyright Airship and Contributors */
package com.urbanairship.automation.rewrite.inappmessage.displayadapter.modal

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.urbanairship.Predicate
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessageActivity
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DelegatingDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayResult
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.InAppMessageDisplayListener
import com.urbanairship.automation.rewrite.utils.ActiveTimer
import java.util.UUID
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * Modal adapter.
 */
internal class ModalDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.ModalContent,
    private val assets: AirshipCachedAssets?,
    private val activityMonitor: ActivityMonitor
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity>? = null

    private var continuation: CancellableContinuation<DisplayResult>? = null

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
            Intent(context, ModalActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
