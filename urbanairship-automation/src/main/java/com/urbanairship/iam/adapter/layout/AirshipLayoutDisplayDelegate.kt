/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.layout

import android.app.Activity
import android.content.Context
import com.urbanairship.Predicate
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.android.layout.util.CachedImage
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.iam.InAppMessageWebViewClient
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.javascript.NativeBridge
import com.urbanairship.json.JsonMap
import com.urbanairship.json.emptyJsonMap
import java.net.MalformedURLException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Airship layout display adapter. */
internal class AirshipLayoutDisplayDelegate(
    private val displayContent: InAppMessageDisplayContent.AirshipLayoutContent,
    private val assets: AirshipCachedAssets?,
    private val priority: Int,
    private val messageExtras: JsonMap?,
    private val activityMonitor: ActivityMonitor,
    private val actionRunner: InAppActionRunner
) : DelegatingDisplayAdapter.Delegate {

    override val activityPredicate: Predicate<Activity>? = null

    private var continuation: CancellableContinuation<DisplayResult>? = null

    @Throws(DisplayException::class, MalformedURLException::class)
    override suspend fun display(
        context: Context, analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {

        val displayListener = LayoutListener(
            analytics = analytics,
            onDismiss = {
                continuation?.resumeWith(Result.success(it))
            }
        )

        val extras = messageExtras ?: emptyJsonMap()

        val request = Thomas.prepareDisplay(
            payload = displayContent.layout.layoutInfo,
            listener = displayListener,
            activityMonitor = activityMonitor,
            actionRunner = actionRunner,
            webViewClientFactory = { InAppMessageWebViewClient( NativeBridge(actionRunner), messageExtras) },
            priority = priority,
            extras = extras,
            imageCache = { url ->
                assets?.cacheUri(url)?.path?.let {
                    val size = assets.getMediaSize(url)

                    CachedImage(
                        path = it,
                        size =  if (size.width > 0 && size.height > 0) {
                            size
                        } else {
                            null
                        }
                    )
                }
            },
            embeddedViewManager = EmbeddedViewManager
        )

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine {
                continuation = it
                request.display(context)

                // If the display is not embedded, we notify the listener that it is visible.
                // If it is embedded, the above request to display will place the embedded layout
                // into the display queue, so we'll need to wait for the content to be displayed
                // before notifying the listener.
                if (!displayContent.layout.isEmbedded()) {
                    displayListener.onVisibilityChanged(true, activityMonitor.isAppForegrounded)
                }
            }
        }
    }
}
