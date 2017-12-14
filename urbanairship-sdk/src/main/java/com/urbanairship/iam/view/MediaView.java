/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.messagecenter.ImageLoader;

/**
 * Media view.
 */
public class MediaView extends FrameLayout {
    private static final int FALLBACK_VIDEO_HEIGHT = 320;

    private WebView webView;
    private WebChromeClient chromeClient;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public MediaView(Context context) {
        this(context, null);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public MediaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public MediaView(Context context, AttributeSet attrs, int defStyle) {
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
    public void setChromeClient(WebChromeClient chromeClient) {
        this.chromeClient = chromeClient;
        if (webView != null) {
            webView.setWebChromeClient(chromeClient);
        }
    }

    /**
     * Sets the media info.
     *
     * @param mediaInfo The media info.
     * @param cachedMediaUrl The cached media URL.
     */
    public void setMediaInfo(final MediaInfo mediaInfo, final String cachedMediaUrl) {
        removeAllViewsInLayout();

        // If we had a web view previously clear it
        if (this.webView != null) {
            this.webView.setWebChromeClient(null);
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
                ImageLoader.shared(getContext()).load(url, 0, imageView);
                break;

            case MediaInfo.TYPE_VIDEO:
                loadWebView(mediaInfo, new VideoLoadCallback() {
                    @Override
                    public void loadVideo(WebView webView, String url, int height) {
                        String html = "<body style=\"margin:0\"><video style=\"display:block;padding:0;margin:0 auto;border:0;width:100%;height:" +
                                height + "px;\" " +
                                "src=\"" + url + "\" " +
                                "controls autobuffer></video></body>";

                        webView.loadData(html, "text/html", "utf-8");
                    }
                });

                break;

            case MediaInfo.TYPE_YOUTUBE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    loadWebView(mediaInfo, new VideoLoadCallback() {

                        @Override
                        public void loadVideo(WebView webView, String url, int height) {
                            String html = "<body style=\"margin:0\"><iframe " +
                                    "style=\"display:block;padding:0;margin:0 auto;border:0;width:100%;" +
                                    "height:" + height + "px;\" " +
                                    "src=\"" + url + "\" " +
                                    "frameborder=\"0\" allow=\"encrypted-media\" allowfullscreen></iframe>" +
                                    "</body>";

                            webView.loadData(html, "text/html", "utf-8");
                        }
                    });
                } else {
                    View placeholder = LayoutInflater.from(getContext()).inflate(R.layout.ua_iam_video_placeholder, this, false);
                    addView(placeholder);
                    placeholder.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mediaInfo.getUrl()));
                            try {
                                view.getContext().startActivity(intent);
                            } catch (Exception e) {
                                Logger.error("Unable to start activity", e);
                            }
                        }
                    });
                }
                break;
        }
    }

    /**
     * Helper method to load video in the webview.
     *
     * @param mediaInfo The media info.
     * @param callback Load callback.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void loadWebView(@NonNull final MediaInfo mediaInfo, @NonNull final VideoLoadCallback callback) {
        this.webView = new WebView(getContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.setWebChromeClient(chromeClient);
        webView.setBackgroundColor(Color.BLACK);
        webView.setContentDescription(mediaInfo.getDescription());

        addView(webView);

        webView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                int height = FALLBACK_VIDEO_HEIGHT;
                if (getHeight() > 0) {
                    DisplayMetrics metrics = getResources().getDisplayMetrics();
                    height = Math.round(getLayoutParams().height / ((float) metrics.densityDpi / DisplayMetrics.DENSITY_DEFAULT));
                }

                webView.getViewTreeObserver().removeOnPreDrawListener(this);
                callback.loadVideo(webView, mediaInfo.getUrl(), height);
                return false;
            }
        });
    }

    /**
     * Video load callback.
     */
    private interface VideoLoadCallback {

        /***
         * Called when the web view is created and the video html needs to be loaded.
         * @param webView The web view.
         * @param url The URL.
         * @param height The video's height.
         */
        void loadVideo(WebView webView, String url, int height);
    }
}
