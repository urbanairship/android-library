/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.android.layout.ThomasListenerInterface;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.webkit.AirshipWebViewClient;

/**
 * Display request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayRequest {
    private final Callback callback;
    private final LayoutInfo payload;
    private ActivityMonitor activityMonitor;
    private ThomasListenerInterface listener;
    private ImageCache imageCache;
    private Factory<AirshipWebViewClient> webViewClientFactory;

    public interface Callback {
        void display(@NonNull Context context, @NonNull DisplayArgs args);
    }

    public DisplayRequest(@NonNull LayoutInfo payload,
                          @NonNull Callback callback) {
        this.payload = payload;
        this.callback = callback;
    }

    @NonNull
    public DisplayRequest setListener(@Nullable ThomasListenerInterface listener) {
        this.listener = listener;
        return this;
    }

    @NonNull
    public DisplayRequest setInAppActivityMonitor(ActivityMonitor activityMonitor) {
        this.activityMonitor = activityMonitor;
        return this;
    }

    @NonNull
    public DisplayRequest setImageCache(@Nullable ImageCache imageCache) {
        this.imageCache = imageCache;
        return this;
    }

    @NonNull
    public DisplayRequest setWebViewClientFactory(@Nullable Factory<AirshipWebViewClient> webViewClientFactory) {
        this.webViewClientFactory = webViewClientFactory;
        return this;
    }

    public void display(@NonNull Context context) {
        DisplayArgs args = new DisplayArgs(payload, listener, activityMonitor, webViewClientFactory, imageCache);
        callback.display(context, args);
    }
}
