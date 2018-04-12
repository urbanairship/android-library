/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.html;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.iam.InAppMessageActivity;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.js.Whitelist;
import com.urbanairship.widget.UAWebView;
import com.urbanairship.widget.UAWebViewClient;

/**
 * HTML in-app message activity.
 */
public class HtmlActivity extends InAppMessageActivity {

    private static final long RETRY_DELAY_MS = 20000; // 20 seconds

    private UAWebView webView;
    private Integer error = null;
    private Handler handler;
    private String url;

    private Runnable delayedLoadRunnable = new Runnable() {
        @Override
        public void run() {
            load();
        }
    };

    @Override
    protected void onCreateMessage(@Nullable Bundle savedInstanceState) {
        final HtmlDisplayContent displayContent = getMessage().getDisplayContent();
        if (displayContent == null) {
            Logger.error("HtmlActivity - Invalid display type: " + getMessage().getDisplayContent());
            finish();
            return;
        }

        setContentView(R.layout.ua_iam_html);
        hideActionBar();
        
        final ProgressBar progressBar = findViewById(R.id.progress);
        final ImageButton dismiss = findViewById(R.id.dismiss);
        this.webView = findViewById(R.id.web_view);
        this.handler = new Handler(Looper.getMainLooper());
        this.url = displayContent.getUrl();

        if (!UAirship.shared().getWhitelist().isWhitelisted(url, Whitelist.SCOPE_OPEN_URL)) {
            Logger.error("HTML in-app message URL is not whitelisted. Unable to display message.");
            finish();
            return;
        }

        // Workaround render issue with older android devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        webView.setAlpha(0);
        webView.setWebViewClient(new UAWebViewClient() {
            @Override
            public void onPageFinished(final WebView view, String url) {
                super.onPageFinished(view, url);
                if (error != null) {
                    switch (error) {
                        case WebViewClient.ERROR_CONNECT:
                        case WebViewClient.ERROR_TIMEOUT:
                        case WebViewClient.ERROR_UNKNOWN:
                            // Retry
                            load(RETRY_DELAY_MS);
                            break;
                        default:
                            // Load an empty page
                            error = null;
                            webView.loadData("", "text/html", null);
                    }
                } else {
                    crossFade(webView, progressBar);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(getIntent().getDataString())) {
                    Logger.error("HtmlActivity - Failed to load page " + failingUrl + " with error " + errorCode + " " + description);
                    error = errorCode;
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {

                // Re-enable hardware rending if we detect a video in the message
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }

                return super.getDefaultVideoPoster();
            }
        });

        // DismissButton
        Drawable dismissDrawable = DrawableCompat.wrap(dismiss.getDrawable()).mutate();
        DrawableCompat.setTint(dismissDrawable, displayContent.getDismissButtonColor());
        dismiss.setImageDrawable(dismissDrawable);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDisplayHandler().finished(ResolutionInfo.dismissed(getDisplayTime()));
                finish();
            }
        });
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();

        load();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
        webView.onPause();

        // Stop any loading
        webView.stopLoading();

        // Cancel any delayed loads
        handler.removeCallbacks(delayedLoadRunnable);
    }


    /**
     * Fades a view in while fading another view out.
     *
     * @param in The view to fade in
     * @param out The view to fade out
     */
    @SuppressLint("NewApi")
    private void crossFade(final View in, final View out) {
        if (in != null) {
            in.animate().alpha(1f).setDuration(200);
        }

        if (out != null) {
            out.animate()
               .alpha(0f)
               .setDuration(200)
               .setListener(new AnimatorListenerAdapter() {
                   @Override
                   public void onAnimationEnd(Animator animation) {
                       out.setVisibility(View.GONE);
                   }
               });
        }
    }


    /**
     * Loads the page.
     */
    protected void load() {
        load(0);
    }

    /**
     * Load the page with a delay.
     *
     * @param delay Delay before loading the page.  Delay of 0 or less
     * will start loading the page immediately.
     */
    @SuppressLint("NewApi")
    protected void load(long delay) {
        if (webView == null) {
            return;
        }

        webView.stopLoading();

        if (delay > 0) {
            handler.postDelayed(delayedLoadRunnable, delay);
            return;
        }

        Logger.info("Loading url: " + url);
        error = null;
        webView.loadUrl(url);
    }
}
