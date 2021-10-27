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
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.property.MediaType;
import com.urbanairship.android.layout.util.ContextUtil;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.webkit.AirshipWebChromeClient;

import java.lang.ref.WeakReference;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Media view.
 *
 * @hide
 */
public class MediaView extends FrameLayout implements BaseView<MediaModel> {
    @Nullable
    private WebView webView;
    @Nullable
    private WebChromeClient chromeClient;

    private static final String VIDEO_HTML_FORMAT =
        "<body style=\"margin:0\"><video playsinline controls height=\"100%%\" width=\"100%%\" src=\"%s\"></video></body>";
    private static final String IMAGE_HTML_FORMAT =
        "<body style=\"margin:0\"><img height=\"100%%\" width=\"100%%\" src=\"%s\"/></body>";

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public MediaView(@NonNull Context context) {
        this(context, null);
        init(context);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
        init(context);
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
        init(context);
    }

    private void init(@NonNull Context context) {
        setId(generateViewId());
        // TODO: this probably shouldn't be happening here...
        setChromeClient(new AirshipWebChromeClient(ContextUtil.getActivityContext(context)));
    }

    @NonNull
    public static MediaView create(@NonNull Context context, @NonNull MediaModel model) {
        MediaView view = new MediaView(context);
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
    @Override
    public void setModel(@NonNull final MediaModel model) {
        removeAllViewsInLayout();

        // If we had a web view previously clear it
        if (this.webView != null) {
            this.webView.stopLoading();
            this.webView.setWebChromeClient(null);
            this.webView.setWebViewClient(null);
            this.webView.destroy();
            this.webView = null;
        }

        switch (model.getMediaType()) {
            case IMAGE:
                loadImage(model);
                break;
            case VIDEO:
            case YOUTUBE:
                loadWebView(model);
                break;
        }
    }

    private void loadImage(@NonNull MediaModel model) {
        String url = model.getUrl();
        if (url.endsWith(".svg")) {
            // Load SVGs in a webview because they won't work in an ImageView
            // TODO: this won't work if the url lacks an extension or if someone
            // gets wacky and the ext doesn't match the actual media type -_-
            loadWebView(model);
        } else {
            ImageView imageView = new ImageView(getContext());
            imageView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            imageView.setAdjustViewBounds(true);
            // TODO: we should allow this to be set for a11y...
            // imageView.setContentDescription(media.getDescription());
            addView(imageView);

            UAirship.shared()
                    .getImageLoader()
                    .load(getContext(), imageView, ImageRequestOptions.newBuilder(url).build());
        }
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param model The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull final MediaModel model) {

        // Default to a 16:9 aspect ratio
        int width = 16;
        int height = 9;

        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            ViewGroup.LayoutParams params = getLayoutParams();

            // Check if we can grow the image horizontally to fit the width
            if (params.height == ViewGroup.LayoutParams.WRAP_CONTENT) {
                float scale = (float) getWidth() / (float) width;
                params.height = Math.round(scale * height);
            } else {
                float imageRatio = (float) width / (float) height;
                float viewRatio = (float) getWidth() / getHeight();

                if (imageRatio >= viewRatio) {
                    // Image is wider than the view
                    params.height = Math.round(getWidth() / imageRatio);
                } else {
                    // View is wider than the image
                    int w = Math.round(getHeight() * imageRatio);
                    if (w > 0) {
                        params.width = w;
                    } else {
                        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                    }
                }
            }

            setLayoutParams(params);
        });

        this.webView = new WebView(getContext());

        FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        LayoutParams webViewLayoutParams = new LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        final ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminate(true);
        progressBar.setId(android.R.id.progress);

        LayoutParams progressBarLayoutParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressBarLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(progressBar, progressBarLayoutParams);

        WebSettings settings = webView.getSettings();
        if (model.getMediaType() == MediaType.VIDEO) {
            settings.setMediaPlaybackRequiresUserGesture(true);
        }
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
            switch (model.getMediaType()) {
                case VIDEO:
                    Logger.debug("LOADING VIDEO");
                    webView.loadData(
                        String.format(Locale.ROOT, VIDEO_HTML_FORMAT, model.getUrl()),
                        "text/html",
                        "UTF-8");
                    break;
                case IMAGE:
                    Logger.debug("LOADING IMAGE");
                    webView.loadData(
                        String.format(Locale.ROOT, IMAGE_HTML_FORMAT, model.getUrl()),
                        "text/html",
                        "UTF-8");
                    break;
                default:
                    Logger.debug("LOADING SOMETHING ELSE");
                    webView.loadUrl(model.getUrl());
                    break;
            }
        };

        webView.setWebChromeClient(chromeClient);
        // TODO: allow this to be set for a11y...
        // webView.setContentDescription(media.getDescription());
        webView.setVisibility(View.INVISIBLE);
        webView.setWebViewClient(new MediaWebViewClient(load) {
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

    private abstract static class MediaWebViewClient extends WebViewClient {
        static final long START_RETRY_DELAY = 1000;

        @NonNull
        private final Runnable onRetry;
        boolean error = false;
        long retryDelay = START_RETRY_DELAY;

        private MediaWebViewClient(@NonNull Runnable onRetry) {
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
