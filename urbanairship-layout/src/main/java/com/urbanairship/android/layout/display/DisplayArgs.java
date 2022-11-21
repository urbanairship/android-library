/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.webkit.AirshipWebViewClient;

/**
 * Display arguments.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class DisplayArgs {
    @NonNull
    private final LayoutInfo payload;
    @NonNull
    private final ThomasListener listener;
    @NonNull
    private final ActivityMonitor inAppActivityMonitor;
    @Nullable
    private final Factory<AirshipWebViewClient> webViewClientFactory;
    @Nullable
    private final ImageCache imageCache;

    public DisplayArgs(
        @NonNull LayoutInfo payload,
        @NonNull ThomasListener listener,
        @NonNull ActivityMonitor inAppActivityMonitor,
        @Nullable Factory<AirshipWebViewClient> webViewClientFactory,
        @Nullable ImageCache imageCache
    ) {
        this.payload = payload;
        this.listener = listener;
        this.inAppActivityMonitor = inAppActivityMonitor;
        this.webViewClientFactory = webViewClientFactory;
        this.imageCache = imageCache;
    }

    @NonNull
    public ThomasListener getListener() {
        return listener;
    }

    @NonNull
    public LayoutInfo getPayload() {
        return payload;
    }

    @NonNull
    public ActivityMonitor getInAppActivityMonitor() {
        return inAppActivityMonitor;
    }

    @Nullable
    public ImageCache getImageCache() {
        return imageCache;
    }

    @Nullable
    public Factory<AirshipWebViewClient> getWebViewClientFactory() {
        return webViewClientFactory;
    }

}
