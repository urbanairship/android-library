/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.display

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.LayoutDataStorage
import com.urbanairship.android.layout.LayoutStateStorage
import com.urbanairship.android.layout.ThomasListenerInterface
import com.urbanairship.android.layout.environment.ThomasActionRunner
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.ImageCache
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.webkit.AirshipWebViewClient

/**
 * Display arguments.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgs(
    public val payload: LayoutInfo,
    public val listener: ThomasListenerInterface,
    public val inAppActivityMonitor: ActivityMonitor,
    public val actionRunner: ThomasActionRunner,
    public val webViewClientFactory: Factory<AirshipWebViewClient>? = null,
    public val imageCache: ImageCache? = null,
    public val stateStorage: LayoutDataStorage? = null
)
