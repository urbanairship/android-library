/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment;

import android.app.Activity;
import android.webkit.WebChromeClient;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;

import com.urbanairship.Predicate;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.webkit.AirshipWebChromeClient;
import com.urbanairship.webkit.AirshipWebViewClient;

import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.webkit.AirshipWebChromeClient;
import com.urbanairship.webkit.AirshipWebViewClient;

/**
 * Environment provided to layout view.
 * @hide
 */
public class DefaultViewEnvironment implements ViewEnvironment {

    @NonNull
    private final Activity activity;

    @NonNull
    private final ActivityMonitor activityMonitor;

    @NonNull
    private final Factory<WebChromeClient> webChromeClientFactory;

    @NonNull
    private final Factory<AirshipWebViewClient> webViewClientFactory;

    @NonNull
    private final ImageCache imageCache;

    private final boolean isIgnoringSafeAreas;

    public DefaultViewEnvironment(
        @NonNull Activity activity,
        @NonNull ActivityMonitor activityMonitor,
        @Nullable Factory<AirshipWebViewClient> webViewClientFactory,
        @Nullable ImageCache imageCache,
        boolean isIgnoringSafeAreas
    ) {
        this.activity = activity;

        this.activityMonitor = activityMonitor;

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

        this.isIgnoringSafeAreas = isIgnoringSafeAreas;
    }

    @NonNull
    @Override
    public Activity activity() {
        return activity;
    }

    @NonNull
    @Override
    public ActivityMonitor activityMonitor() {
        return activityMonitor;
    }

    @NonNull
    @Override
    public Predicate<Activity> hostingActivityPredicate() {
        return activityToCheck -> activityToCheck == activity;
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

    @Override
    public boolean isIgnoringSafeAreas() {
        return isIgnoringSafeAreas;
    }
}
