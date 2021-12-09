/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.webkit.WebView;

import com.urbanairship.javascript.JavaScriptEnvironment;
import com.urbanairship.javascript.NativeBridge;
import com.urbanairship.json.JsonMap;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * AirshipWebViewClient that injects the messages extras in the native bridge.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InAppMessageWebViewClient extends AirshipWebViewClient {

    private final InAppMessage message;

    public InAppMessageWebViewClient(InAppMessage message) {
        this.message = message;
    }

    @VisibleForTesting
    protected InAppMessageWebViewClient(@NonNull NativeBridge nativeBridge, @NonNull InAppMessage message) {
        super(nativeBridge);
        this.message = message;
    }

    @CallSuper
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected JavaScriptEnvironment.Builder extendJavascriptEnvironment(@NonNull JavaScriptEnvironment.Builder builder, @NonNull WebView webView) {
        JsonMap extras = message.getExtras();
        return super.extendJavascriptEnvironment(builder, webView)
                    .addGetter("getMessageExtras", extras);
    }
}
