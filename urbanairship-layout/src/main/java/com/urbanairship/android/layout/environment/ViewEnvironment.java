/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment;

import android.webkit.WebChromeClient;

import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.webkit.AirshipWebChromeClient;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

/**
 * Environment provided to layout view.
 * @hide
 */
public class ViewEnvironment implements Environment {

    @NonNull
    private final ComponentActivity activity;

    @NonNull
    private final Factory<WebChromeClient> webChromeClientFactory;

    @NonNull
    private final Factory<AirshipWebViewClient> webViewClientFactory;

    @NonNull
    private final ImageCache imageCache;


    public ViewEnvironment(@NonNull ComponentActivity activity,
                           @Nullable Factory<AirshipWebViewClient> webViewClientFactory,
                           @Nullable ImageCache imageCache) {
        this.activity = activity;
        this.webChromeClientFactory = () -> new AirshipWebChromeClient(activity);

        if (webViewClientFactory != null) {
            this.webViewClientFactory = webViewClientFactory;
        } else {
            this.webViewClientFactory = AirshipWebViewClient::new;
        }

        if (imageCache != null) {
            this.imageCache = imageCache;
        } else {
            this.imageCache = url -> null;
        }
    }

    @NonNull
    @Override
    public Lifecycle lifecycle() {
        return activity.getLifecycle();
    }

    @NonNull
    public Factory<WebChromeClient> webChromeClientFactory() {
        return webChromeClientFactory;
    }

    @NonNull
    @Override
    public Factory<AirshipWebViewClient> webViewClientFactory() {
        return webViewClientFactory;
    }

    @NonNull
    @Override
    public ImageCache imageCache() {
        return imageCache;
    }

}
