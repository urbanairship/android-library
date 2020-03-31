package com.urbanairship.webkit;

import android.os.Build;
import android.webkit.WebView;

import com.urbanairship.javascript.JavaScriptExecutor;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;

public class WebViewJavaScriptExecutor implements JavaScriptExecutor {

    private WeakReference<WebView> weakReference;

    public WebViewJavaScriptExecutor(@NonNull WebView webView) {
        this.weakReference = new WeakReference<>(webView);
    }

    @Override
    public void executeJavaScript(@NonNull String javaScript) {
        WebView webView = weakReference.get();
        if (webView == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 19) {
            webView.evaluateJavascript(javaScript, null);
        } else {
            webView.loadUrl("javascript:" + javaScript);
        }
    }
}
