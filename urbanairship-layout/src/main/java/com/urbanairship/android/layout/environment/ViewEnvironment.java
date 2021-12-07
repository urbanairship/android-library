/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment;

import android.webkit.WebChromeClient;

import com.urbanairship.android.layout.util.Factory;
import com.urbanairship.webkit.AirshipWebChromeClient;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;

/**
 * Environment provided to layout view.
 * @hide
 */
public class ViewEnvironment implements Environment {

    @NonNull
    private final ComponentActivity activity;

    public ViewEnvironment(@NonNull ComponentActivity activity) {
        this.activity = activity;
    }

    @NonNull
    @Override
    public Lifecycle lifecycle() {
        return activity.getLifecycle();
    }

    @NonNull
    public Factory<WebChromeClient> webChromeClientFactory() {
        return () -> new AirshipWebChromeClient(activity);
    }
}
