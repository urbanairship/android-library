/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.environment

import android.app.Activity
import android.webkit.WebChromeClient
import androidx.annotation.RestrictTo
import com.urbanairship.Predicate
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.webkit.AirshipWebViewClient

/**
 * Environment provided to layout views.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ViewEnvironment {
    public fun activityMonitor(): ActivityMonitor
    public fun hostingActivityPredicate(): Predicate<Activity>
    public fun webChromeClientFactory(): Factory<WebChromeClient>
    public fun webViewClientFactory(): Factory<AirshipWebViewClient>
    public fun imageCache(): ImageCache
    public val isIgnoringSafeAreas: Boolean
}
