/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.display;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.webkit.AirshipWebViewClient;

/**
 * Display request.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DisplayRequest {
    private final Callback callback;
    private final LayoutInfo payload;
    private ThomasListener listener;
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
    public DisplayRequest setListener(@Nullable ThomasListener listener) {
        this.listener = listener;
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
        DisplayArgs args = new DisplayArgs(payload, listener, webViewClientFactory, imageCache);
        callback.display(context, args);
    }
}
