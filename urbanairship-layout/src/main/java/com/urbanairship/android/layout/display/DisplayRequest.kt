/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.display

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.ai.InternalAirshipAi
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.webkit.AirshipWebViewClient

/**
 * Display request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayRequest(
    private val payload: LayoutInfo,
    private var activityMonitor: ActivityMonitor,
    private var listener: ThomasListenerInterface,
    private var actionRunner: ThomasActionRunner,
    private var imageCache: ImageCache? = null,
    private var webViewClientFactory: Factory<AirshipWebViewClient>? = null,
    private val ai: InternalAirshipAi? = null,
    private val onDisplay: (context: Context, displayArgs: DisplayArgs) -> Unit
) {

    public fun display(context: Context) {
        val args = DisplayArgs(
            payload = payload,
            listener = listener,
            inAppActivityMonitor = activityMonitor,
            actionRunner = actionRunner,
            webViewClientFactory = webViewClientFactory,
            imageCache = imageCache,
            ai = ai
        )
        onDisplay(context, args)
    }
}
