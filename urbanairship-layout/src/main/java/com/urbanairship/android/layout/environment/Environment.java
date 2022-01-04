/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment;

import android.webkit.WebChromeClient;

import com.urbanairship.android.layout.reporting.DisplayTimer;
import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.android.layout.util.ImageCache;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.Lifecycle;

/**
 * Environment provided to layout views.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Environment {
    @NonNull
    Lifecycle lifecycle();

    @NonNull
    Factory<WebChromeClient> webChromeClientFactory();

    @NonNull
    Factory<AirshipWebViewClient> webViewClientFactory();

    @NonNull
    ImageCache imageCache();

    @NonNull
    DisplayTimer displayTimer();

    boolean isIgnoringSafeAreas();
}
