/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.model.WebViewModel;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.webkit.AirshipWebView;
import com.urbanairship.webkit.AirshipWebViewClient;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Web view... view? */
public class WebViewView extends FrameLayout implements BaseView {
    private final WebViewModel model;
    private final ViewEnvironment viewEnvironment;

    @Nullable
    private WebView webView;
    @Nullable
    private WebChromeClient chromeClient;

    public WebViewView(
        @NonNull Context context,
        @NonNull WebViewModel model,
        @NonNull ViewEnvironment viewEnvironment
    ) {
        super(context, null);
        this.model = model;
        this.viewEnvironment = viewEnvironment;

        setId(model.getViewId());
        configure();
    }

    /**
     * Sets the chrome client when loading videos.
     *
     * @param chromeClient The web chrome client.
     */
    public void setChromeClient(@Nullable WebChromeClient chromeClient) {
        this.chromeClient = chromeClient;
        if (webView != null) {
            webView.setWebChromeClient(chromeClient);
        }
    }

    private void configure() {
        viewEnvironment.lifecycle().addObserver(lifecycleListener);
        setChromeClient(viewEnvironment.webChromeClientFactory().create());

        LayoutUtils.applyBorderAndBackground(this, model);

        loadWebView(model);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull WebViewModel model) {
        this.webView = new AirshipWebView(getContext());

        // Restore saved state from the model, if available.
        Bundle savedState = model.getSavedState();
        if (savedState != null) {
            webView.restoreState(savedState);
        }

        FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LayoutParams webViewLayoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminate(true);
        progressBar.setId(android.R.id.progress);

        LayoutParams progressBarLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressBarLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(progressBar, progressBarLayoutParams);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);

        if (ManifestUtils.shouldEnableLocalStorage()) {
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
        }

        AirshipWebViewClient client = viewEnvironment.webViewClientFactory().create();
        client.addListener(new ClientListener() {
            @Override
            protected void onPageFinished(@NonNull WebView webView) {
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(GONE);
            }

            @Override
            protected void onRetry(@NonNull WebView webView) {
                webView.loadUrl(model.getUrl());
            }

            @Override
            public boolean onClose(@NonNull WebView webView) {
                model.onClose();
                return true;
            }
        });

        webView.setWebChromeClient(chromeClient);
        webView.setVisibility(View.INVISIBLE);
        webView.setWebViewClient(client);

        addView(frameLayout);

        if (!UAirship.shared().getUrlAllowList().isAllowed(model.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("URL not allowed. Unable to load: %s", model.getUrl());
            return;
        }

        // Load the URL (if we didn't restore with saved state)
        if (savedState == null) {
            webView.loadUrl(model.getUrl());
        }
    }

    private abstract static class ClientListener implements AirshipWebViewClient.Listener {
        static final long START_RETRY_DELAY = 1000;

        boolean error = false;
        long retryDelay = START_RETRY_DELAY;


        @Override
        public void onPageFinished(@NonNull WebView view, @Nullable String url) {
            if (error) {
                WeakReference<WebView> webViewWeakReference = new WeakReference<>(view);
                view.postDelayed(() -> {
                    WebView webView = webViewWeakReference.get();
                    if (webView != null) {
                        onRetry(webView);
                    }
                }, retryDelay);
                retryDelay = retryDelay * 2;
            } else {
                onPageFinished(view);
            }

            error = false;
        }

        @Override
        public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Logger.error("Error loading web view! %d - %s", error.getErrorCode(), error.getDescription());
            } else {
                Logger.error("Error loading web view!");
            }
            this.error = true;
        }

        protected abstract void onPageFinished(@NonNull WebView webView);

        protected abstract void onRetry(@NonNull WebView webView);

    }

    private final LifecycleObserver lifecycleListener = new DefaultLifecycleObserver() {
        @Override
        public void onPause(@NonNull LifecycleOwner owner) {
            if (webView != null) {
                webView.onPause();
            }
        }

        @Override
        public void onResume(@NonNull LifecycleOwner owner) {
            if (webView != null) {
                webView.onResume();
            }
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            // WebView saved state is meant to work with Activity/Fragment APIs that use bundles.
            // Work around this by stashing state in the model instead. This won't survive process
            // restarts, but will at least restore scroll position for recreates.
            if (webView != null) {
                Bundle bundle = new Bundle();
                webView.saveState(bundle);
                model.saveState(bundle);
            }
        }
    };
}
