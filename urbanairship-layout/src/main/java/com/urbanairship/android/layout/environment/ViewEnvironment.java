/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment;

import android.app.Activity;
import android.webkit.WebChromeClient;

import com.urbanairship.Predicate;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Environment provided to layout views.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface ViewEnvironment {

    @NonNull
    ActivityMonitor activityMonitor();

    @NonNull
    Predicate<Activity> hostingActivityPredicate();

    @NonNull
    Factory<WebChromeClient> webChromeClientFactory();

    @NonNull
    Factory<AirshipWebViewClient> webViewClientFactory();

    @NonNull
    ImageCache imageCache();

    boolean isIgnoringSafeAreas();
}
