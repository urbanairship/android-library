/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
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
import com.urbanairship.android.layout.environment.Environment;
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
public class WebViewView extends FrameLayout implements BaseView<WebViewModel> {
    private WebViewModel model;
    private Environment environment;

    @Nullable
    private WebView webView;
    @Nullable
    private WebChromeClient chromeClient;

    public WebViewView(@NonNull Context context) {
        this(context, null);
        init();
    }

    public WebViewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
        init();
    }

    public WebViewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private void init() {
        setId(generateViewId());
    }

    @NonNull
    public static WebViewView create(@NonNull Context context, @NonNull WebViewModel model, Environment environment) {
        WebViewView view = new WebViewView(context);
        view.setModel(model, environment);
        return view;
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

    /**
     * Sets the media info.
     *  @param model The media info.
     * // TODO: @param cachedMediaUrl The cached media URL.
     * @param environment
     */
    public void setModel(@NonNull WebViewModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;
        configure();
    }

    private void configure() {
        removeAllViewsInLayout();

        // If we had a web view previously clear it
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.setWebChromeClient(null);
            this.webView.setWebViewClient(null);
            this.webView.destroy();
            this.webView = null;
        }

        environment.lifecycle().addObserver(lifecycleListener);
        setChromeClient(environment.webChromeClientFactory().create());

        LayoutUtils.applyBorderAndBackground(this, model);

        loadWebView(model);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull WebViewModel model) {
        this.webView = new AirshipWebView(getContext());

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

        WeakReference<WebView> webViewWeakReference = new WeakReference<>(webView);

        Runnable load = () -> {
            WebView webView = webViewWeakReference.get();
            if (webView == null) {
                return;
            }
            webView.loadUrl(model.getUrl());
        };

        webView.setWebChromeClient(chromeClient);
        webView.setVisibility(View.INVISIBLE);
        webView.setWebViewClient(new Client(load) {
            @Override
            protected void onPageFinished(@NonNull WebView webView) {
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(GONE);
            }
        });

        addView(frameLayout);

        if (!UAirship.shared().getUrlAllowList().isAllowed(model.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("URL not allowed. Unable to load: %s", model.getUrl());
            return;
        }

        load.run();
    }

    private abstract static class Client extends AirshipWebViewClient {
        static final long START_RETRY_DELAY = 1000;

        private final Runnable onRetry;
        boolean error = false;
        long retryDelay = START_RETRY_DELAY;

        private Client(Runnable onRetry) {
            this.onRetry = onRetry;
        }

        @Override
        public void onPageFinished(@Nullable WebView view, String url) {
            super.onPageFinished(view, url);
            if (view == null) {
                return;
            }

            if (error) {
                view.postDelayed(onRetry, retryDelay);
                retryDelay = retryDelay * 2;
            } else {
                onPageFinished(view);
            }

            error = false;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, @NonNull WebResourceError error) {
            super.onReceivedError(view, request, error);
            Logger.error("Error loading web view! %d - %s", error.getErrorCode(), error.getDescription());
            this.error = true;
        }

        protected abstract void onPageFinished(WebView webView);
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
    };
}
