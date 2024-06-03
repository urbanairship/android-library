/* Copyright Airship and Contributors */
package com.urbanairship.iam.adapter.layout

import android.app.Activity
import android.content.Context
import com.urbanairship.Predicate
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.display.DisplayException
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.embedded.EmbeddedViewManager
import com.urbanairship.iam.InAppMessageWebViewClient
import com.urbanairship.iam.adapter.DelegatingDisplayAdapter
import com.urbanairship.iam.adapter.DisplayResult
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
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
    private val messageExtras: JsonMap?,
    private val activityMonitor: ActivityMonitor
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

        val request =
            Thomas.prepareDisplay(displayContent.layout.layoutInfo, extras, EmbeddedViewManager)
                .setListener(displayListener)
                .setImageCache { url -> assets?.cacheUri(url)?.path }
                .setWebViewClientFactory { InAppMessageWebViewClient(messageExtras) }
                .setInAppActivityMonitor(activityMonitor)

        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine {
                continuation = it
                request.display(context)
                displayListener.onVisibilityChanged(true, activityMonitor.isAppForegrounded)
            }
        }
    }
}
