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
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.model.WebViewModel;
import com.urbanairship.android.layout.util.ContextUtil;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.webkit.AirshipWebChromeClient;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/** Web view... view? */
public class WebViewView extends FrameLayout implements BaseView<WebViewModel> {
    @Nullable
    private WebView webView;
    @Nullable
    private WebChromeClient chromeClient;

    public WebViewView(@NonNull Context context) {
        this(context, null);
        init(context);
    }

    public WebViewView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
        init(context);
    }

    public WebViewView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    private void init(@NonNull Context context) {
        setId(generateViewId());

        // TODO: This should probably happen elsewhere?
        setChromeClient(new AirshipWebChromeClient(ContextUtil.getActivityContext(context)));
    }

    @NonNull
    public static WebViewView create(@NonNull Context context, @NonNull WebViewModel model) {
        WebViewView view = new WebViewView(context);
        view.setModel(model);
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
     * Call during activity pause to pause the media.
     */
    public void onPause() {
        // TODO: if things stay as they are, we'll need to pull in lifecycle to handle this
        if (this.webView != null) {
            this.webView.onPause();
        }
    }

    /**
     * Call during activity resume to resume the media.
     */
    public void onResume() {
        // TODO: if things stay as they are, we'll need to pull in lifecycle to handle this
        if (this.webView != null) {
            this.webView.onResume();
        }
    }

    /**
     * Sets the media info.
     *
     * @param model The media info.
     * // TODO: @param cachedMediaUrl The cached media URL.
     */
    public void setModel(@NonNull final WebViewModel model) {
        removeAllViewsInLayout();

        // If we had a web view previously clear it
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.setWebChromeClient(null);
            this.webView.setWebViewClient(null);
            this.webView.destroy();
            this.webView = null;
        }

        loadWebView(model);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull final WebViewModel model) {
        this.webView = new WebView(getContext());

        FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        LayoutParams webViewLayoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        final ProgressBar progressBar = new ProgressBar(getContext());
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

        final WeakReference<WebView> webViewWeakReference = new WeakReference<>(webView);

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

    private abstract static class Client extends WebViewClient {
        static final long START_RETRY_DELAY = 1000;

        private final Runnable onRetry;
        boolean error = false;
        long retryDelay = START_RETRY_DELAY;

        private Client(Runnable onRetry) {
            this.onRetry = onRetry;
        }

        @Override
        public void onPageFinished(@NonNull WebView view, final String url) {
            super.onPageFinished(view, url);
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
}
