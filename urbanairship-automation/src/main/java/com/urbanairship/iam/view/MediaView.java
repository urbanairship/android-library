/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
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
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Media view.
 *
 * @hide
 */
public class MediaView extends FrameLayout {

    private WebView webView;
    private WebChromeClient chromeClient;

    private static final String VIDEO_HTML_FORMAT = "<body style=\"margin:0\"><video playsinline controls height=\"100%%\" width=\"100%%\" src=\"%s\"></video></body>";

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public MediaView(@NonNull Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or cannot be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);
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
        if (this.webView != null) {
            this.webView.onPause();
        }
    }

    /**
     * Call during activity resume to resume the media.
     */
    public void onResume() {
        if (this.webView != null) {
            this.webView.onResume();
        }
    }

    /**
     * Sets the media info.
     *
     * @param mediaInfo The media info.
     * @param cachedMediaUrl The cached media URL.
     */
    public void setMediaInfo(@NonNull final MediaInfo mediaInfo, @Nullable final String cachedMediaUrl) {
        removeAllViewsInLayout();

        // If we had a web view previously clear it
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.setWebChromeClient(null);
            this.webView.setWebViewClient(null);
            this.webView.destroy();
            this.webView = null;
        }

        switch (mediaInfo.getType()) {
            case MediaInfo.TYPE_IMAGE:
                ImageView imageView = new ImageView(getContext());
                imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                imageView.setAdjustViewBounds(true);
                imageView.setContentDescription(mediaInfo.getDescription());
                addView(imageView);

                String url = cachedMediaUrl == null ? mediaInfo.getUrl() : cachedMediaUrl;
                UAirship.shared().getImageLoader()
                        .load(getContext(), imageView, ImageRequestOptions.newBuilder(url).build());
                break;

            case MediaInfo.TYPE_VIDEO:
            case MediaInfo.TYPE_YOUTUBE:
                loadWebView(mediaInfo);
                break;
        }
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param mediaInfo The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull final MediaInfo mediaInfo) {
        this.webView = new WebView(getContext());

        FrameLayout frameLayout = new FrameLayout(getContext());
        FrameLayout.LayoutParams webViewLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        final ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminate(true);
        progressBar.setId(android.R.id.progress);

        FrameLayout.LayoutParams progressBarLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressBarLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(progressBar, progressBarLayoutParams);

        WebSettings settings = webView.getSettings();
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setJavaScriptEnabled(true);

        if (ManifestUtils.shouldEnableLocalStorage()) {
            settings.setDomStorageEnabled(true);
            settings.setDatabaseEnabled(true);
        }

        final WeakReference<WebView> webViewWeakReference = new WeakReference<>(webView);

        Runnable load = new Runnable() {
            @Override
            public void run() {
                WebView webView = webViewWeakReference.get();
                if (webView == null) {
                    return;
                }

                if (MediaInfo.TYPE_VIDEO.equals(mediaInfo.getType())) {
                    webView.loadData(String.format(Locale.ROOT, VIDEO_HTML_FORMAT, mediaInfo.getUrl()), "text/html", "UTF-8");
                } else {
                    webView.loadUrl(mediaInfo.getUrl());
                }
            }
        };

        webView.setWebChromeClient(chromeClient);
        webView.setContentDescription(mediaInfo.getDescription());
        webView.setVisibility(View.INVISIBLE);
        webView.setWebViewClient(new MediaWebViewClient(load) {
            @Override
            protected void onPageFinished(@NonNull WebView webView) {
                webView.setVisibility(View.VISIBLE);
                progressBar.setVisibility(GONE);
            }
        });

        addView(frameLayout);

        if (!UAirship.shared().getUrlAllowList().isAllowed(mediaInfo.getUrl(), UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("URL not allowed. Unable to load: %s", mediaInfo.getUrl());
            return;
        }

        load.run();
    }

    private static abstract class MediaWebViewClient extends WebViewClient {

        static final long START_RETRY_DELAY = 1000;

        private final Runnable onRetry;
        boolean error = false;
        long retryDelay = START_RETRY_DELAY;

        private MediaWebViewClient(Runnable onRetry) {
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
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            this.error = true;
        }

        protected abstract void onPageFinished(WebView webView);

    }

}
