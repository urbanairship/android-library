/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.layout

import android.app.Activity
import android.content.Context
import com.urbanairship.Predicate
import com.urbanairship.UALog
import com.urbanairship.ai.InternalAirshipAi
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.analytics.DisplayResult
import com.urbanairship.android.layout.analytics.LayoutListener
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.android.layout.util.CachedImage
import com.urbanairship.android.layout.util.ExtendableImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.banner.BannerViewManager
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.iam.InAppMessageWebViewClient
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.android.layout.assets.AirshipCachedAssets
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
    private val actionRunner: InAppActionRunner,
    private val ai: InternalAirshipAi? = null
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
                continuation?.let { continuation ->
                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(it))
                    }
                }
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
            imageCache = ExtendableImageCache { url ->
                val cachedPath = assets?.cacheUri(url)?.path
                if (cachedPath != null && assets.isCached(url)) {
                    val size = assets.getMediaSize(url)
                    CachedImage(path = cachedPath, size = if (size.width > 0 && size.height > 0) size else null)
                } else {
                    // Returning null to fetch from remote URL directly
                    UALog.d { "AirshipLayoutDisplayDelegate: image cache missing, url=$url" }
                    null
                }
            },
            embeddedViewManager = EmbeddedViewManager,
            bannerViewManager = BannerViewManager,
            ai = ai
        )

        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine {
                continuation = it
                request.display(context)

                // If the display is not embedded or a banner, we notify the listener that it is
                // visible. Embedded layouts and banners are placed into a display queue by the
                // above request to display, so we'll need to wait for the content to be displayed
                // before notifying the listener.
                if (!displayContent.layout.isEmbedded() && !displayContent.layout.isBanner()) {
                    displayListener.onVisibilityChanged(true, activityMonitor.isAppForegrounded)
                }
            }
        }
    }
}
