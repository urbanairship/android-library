/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.app.Activity
import android.webkit.WebChromeClient
import androidx.annotation.RestrictTo
import com.urbanairship.Predicate
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.webkit.AirshipWebChromeClient
import com.urbanairship.webkit.AirshipWebViewClient

/**
 * Environment provided to layout view.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultViewEnvironment(
    private val activity: Activity,
    private val activityMonitor: ActivityMonitor,
    webViewClientFactory: Factory<AirshipWebViewClient>?,
    imageCache: ImageCache?,
    override val isIgnoringSafeAreas: Boolean
) : ViewEnvironment {

    private val webChromeClientFactory: Factory<WebChromeClient> =
         Factory { AirshipWebChromeClient(activity) }

    private val webViewClientFactory: Factory<AirshipWebViewClient> =
        webViewClientFactory ?: Factory { AirshipWebViewClient() }

    private val imageCache: ImageCache = imageCache ?: ImageCache { null }

    override fun activityMonitor(): ActivityMonitor = activityMonitor

    override fun hostingActivityPredicate(): Predicate<Activity> =
        Predicate { activityToCheck: Activity -> activityToCheck === activity }

    public override fun webChromeClientFactory(): Factory<WebChromeClient> = webChromeClientFactory
    public override fun webViewClientFactory(): Factory<AirshipWebViewClient> = webViewClientFactory
    public override fun imageCache(): ImageCache = imageCache
}
