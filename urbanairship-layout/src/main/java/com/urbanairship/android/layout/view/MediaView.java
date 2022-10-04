/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
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
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.model.MediaModel;
import com.urbanairship.android.layout.property.MediaType;
import com.urbanairship.android.layout.util.LayoutUtils;
import com.urbanairship.android.layout.util.ResourceUtils;
import com.urbanairship.images.ImageRequestOptions;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;

import java.lang.ref.WeakReference;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

/**
 * Media view.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class MediaView extends FrameLayout implements BaseView<MediaModel> {

    private MediaModel model;
    private Environment environment;

    @Nullable
    private WebView webView;

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
        init();
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public MediaView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
        init();
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
        init();
    }

    private void init() {
    }

    @NonNull
    public static MediaView create(@NonNull Context context, @NonNull MediaModel model, @NonNull Environment environment) {
        MediaView view = new MediaView(context);
        view.setModel(model, environment);
        return view;
    }

    /**
     * Sets the media info.
     *
     * @param model The media info.
     * @param environment The environment.
     */
    @Override
    public void setModel(@NonNull MediaModel model, @NonNull Environment environment) {
        this.model = model;
        this.environment = environment;

        setId(model.getViewId());
        configure();
    }

    private void configure() {
        removeAllViewsInLayout();

        LayoutUtils.applyBorderAndBackground(this, model);

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
                configureImage(model);
                break;
            case VIDEO:
            case YOUTUBE:
                configureVideo(model);
                break;
        }
    }

    private void configureImage(@NonNull MediaModel model) {
        String url = model.getUrl();
        String cachedImage = environment.imageCache().get(url);
        if (cachedImage != null) {
            url = cachedImage;
        }

        if (url.endsWith(".svg")) {
            // Load SVGs in a webview because they won't work in an ImageView
            // TODO: this won't work if the url lacks an extension or if someone
            // gets wacky and the ext doesn't match the actual media type -_-
            configureVideo(model);
            return;
        }

        ImageView imageView = new ImageView(getContext());
        imageView.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(model.getScaleType());

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            imageView.setContentDescription(model.getContentDescription());
        }

        addView(imageView);

        // Falling back to the screen dimensions keeps the image as large as possible,
        // while still allowing for sampling to occur.
        int fallbackWidth = ResourceUtils.getDisplayWidthPixels(getContext());
        int fallbackHeight = ResourceUtils.getDisplayHeightPixels(getContext());

        ImageRequestOptions options = ImageRequestOptions
            .newBuilder(url)
            .setFallbackDimensions(fallbackWidth, fallbackHeight)
            .build();

        UAirship.shared().getImageLoader()
                .load(getContext(), imageView, options);
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param model The media info.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void configureVideo(@NonNull MediaModel model) {

        // Default to a 16:9 aspect ratio
        int width = 16;
        int height = 9;

        getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            ViewGroup.LayoutParams params = getLayoutParams();

            // Check if we can grow the image horizontally to fit the width
            if (params.height == WRAP_CONTENT) {
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
                        params.width = MATCH_PARENT;
                    }
                }
            }

            setLayoutParams(params);
        });

        environment.lifecycle().addObserver(lifecycleListener);

        this.webView = new WebView(getContext());
        this.webView.setWebChromeClient(environment.webChromeClientFactory().create());

        FrameLayout frameLayout = new FrameLayout(getContext());
        frameLayout.setLayoutParams(new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        LayoutParams webViewLayoutParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminate(true);
        progressBar.setId(android.R.id.progress);

        LayoutParams progressBarLayoutParams = new LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
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

        WeakReference<WebView> webViewWeakReference = new WeakReference<>(webView);

        Runnable load = () -> {
            WebView webView = webViewWeakReference.get();
            if (webView == null) {
                return;
            }
            switch (model.getMediaType()) {
                case VIDEO:
                    webView.loadData(
                            String.format(Locale.ROOT, VIDEO_HTML_FORMAT, model.getUrl()),
                            "text/html",
                            "UTF-8");
                    break;
                case IMAGE:
                    webView.loadData(
                            String.format(Locale.ROOT, IMAGE_HTML_FORMAT, model.getUrl()),
                            "text/html",
                            "UTF-8");
                    break;
                default:
                    webView.loadUrl(model.getUrl());
                    break;
            }
        };

        if (!UAStringUtil.isEmpty(model.getContentDescription())) {
            webView.setContentDescription(model.getContentDescription());
        }
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
        public void onPageFinished(@NonNull WebView view, String url) {
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
        public void onDestroy(@NonNull LifecycleOwner owner) {
            webView = null;
            environment.lifecycle().removeObserver(lifecycleListener);
        }
    };
}
