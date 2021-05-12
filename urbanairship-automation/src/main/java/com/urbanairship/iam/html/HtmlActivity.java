/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.automation.R;
import com.urbanairship.iam.InAppMessageActivity;
import com.urbanairship.iam.ResolutionInfo;
import com.urbanairship.iam.view.BoundedFrameLayout;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.webkit.AirshipWebChromeClient;
import com.urbanairship.webkit.AirshipWebView;

import java.lang.ref.WeakReference;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.DrawableCompat;

/**
 * HTML in-app message activity.
 */
public class HtmlActivity extends InAppMessageActivity {

    private static final long RETRY_DELAY_MS = 20000; // 20 seconds

    private AirshipWebView webView;
    private Integer error = null;
    private Handler handler;
    private String url;
    private final Runnable delayedLoadRunnable = new Runnable() {
        @Override
        public void run() {
            load();
        }
    };

    @Override
    protected void onCreateMessage(@Nullable Bundle savedInstanceState) {
        if (getMessage() == null) {
            finish();
            return;
        }

        final HtmlDisplayContent displayContent = getMessage().getDisplayContent();
        if (displayContent == null) {
            Logger.error("HtmlActivity - Invalid display type: %s", getMessage().getDisplayContent());
            finish();
            return;
        }

        float borderRadius = 0;

        if (isFullScreen(displayContent)) {
            setTheme(R.style.UrbanAirship_InAppHtml_Activity_Fullscreen);
            setContentView(R.layout.ua_iam_html_fullscreen);
        } else {
            setContentView(R.layout.ua_iam_html);
            borderRadius = displayContent.getBorderRadius();
        }

        final ProgressBar progressBar = findViewById(R.id.progress);
        final ImageButton dismiss = findViewById(R.id.dismiss);
        final BoundedFrameLayout content = findViewById(R.id.content_holder);

        applySizeConstraints(displayContent);

        this.webView = findViewById(R.id.web_view);
        this.handler = new Handler(Looper.getMainLooper());
        this.url = displayContent.getUrl();

        if (!UAirship.shared().getUrlAllowList().isAllowed(url, UrlAllowList.SCOPE_OPEN_URL)) {
            Logger.error("HTML in-app message URL is not allowed. Unable to display message.");
            finish();
            return;
        }

        webView.setWebViewClient(new HtmlWebViewClient(getMessage()) {
            @Override
            public void onMessageDismissed(@NonNull JsonValue argument) {
                try {
                    ResolutionInfo info = ResolutionInfo.fromJson(argument);

                    if (getDisplayHandler() != null) {
                        getDisplayHandler().finished(info, getDisplayTime());
                    }

                    finish();

                } catch (JsonException e) {
                    Logger.error("Unable to parse message resolution JSON", e);
                }
            }

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
                    Logger.error("HtmlActivity - Failed to load page %s with error %s %s", failingUrl, errorCode, description);
                    error = errorCode;
                }
            }
        });

        webView.setAlpha(0);
        webView.getSettings().setSupportMultipleWindows(true);
        webView.setWebChromeClient(new AirshipWebChromeClient(this));

        // DismissButton
        Drawable dismissDrawable = DrawableCompat.wrap(dismiss.getDrawable()).mutate();
        DrawableCompat.setTint(dismissDrawable, displayContent.getDismissButtonColor());
        dismiss.setImageDrawable(dismissDrawable);
        dismiss.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (getDisplayHandler() != null) {
                    getDisplayHandler().finished(ResolutionInfo.dismissed(), getDisplayTime());
                }
                finish();
            }
        });

        content.setBackgroundColor(displayContent.getBackgroundColor());

        if (borderRadius > 0) {
            content.setClipPathBorderRadius(borderRadius);
        }
    }

    private boolean isFullScreen(HtmlDisplayContent displayContent) {
        if (!displayContent.isFullscreenDisplayAllowed()) {
            return false;
        }

        return getResources().getBoolean(R.bool.ua_iam_html_allow_fullscreen_display);
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();

        load();
    }

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
    private void crossFade(@Nullable final View in, @Nullable final View out) {
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
    protected void load(long delay) {
        if (webView == null) {
            return;
        }

        webView.stopLoading();

        if (delay > 0) {
            handler.postDelayed(delayedLoadRunnable, delay);
            return;
        }

        Logger.info("Loading url: %s", url);
        error = null;
        webView.loadUrl(url);
    }

    public void applySizeConstraints(@NonNull HtmlDisplayContent displayContent) {
        if (displayContent.getWidth() == 0 && displayContent.getHeight() == 0) {
            return;
        }

        View view = findViewById(R.id.content_holder);
        if (view == null) {
            return;
        }

        final int width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, displayContent.getWidth(), getResources().getDisplayMetrics());
        final int height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, displayContent.getHeight(), getResources().getDisplayMetrics());
        final boolean aspectLock = displayContent.getAspectRatioLock();

        final WeakReference<View> viewWeakReference = new WeakReference<>(view);
        view.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                View view = viewWeakReference.get();
                if (view == null) {
                    return true;
                }

                ViewGroup.LayoutParams params = view.getLayoutParams();

                int parentWidth = view.getMeasuredWidth();
                int parentHeight = view.getMeasuredHeight();

                int normalizedWidth = Math.min(parentWidth, width);
                int normalizedHeight = Math.min(parentHeight, height);

                if (aspectLock && (normalizedWidth != width || normalizedHeight != height)) {
                    float landingPageAspect = (float) width / height;
                    float parentAspect = (float) parentWidth / parentHeight;

                    if (parentAspect > landingPageAspect) {
                        normalizedWidth = (int) ((float) width * parentHeight / height);
                    } else {
                        normalizedHeight = (int) ((float) height * parentWidth / width);
                    }
                }

                if (normalizedHeight > 0) {
                    params.height = normalizedHeight;
                }

                if (normalizedWidth > 0) {
                    params.width = normalizedWidth;
                }

                view.setLayoutParams(params);

                view.getViewTreeObserver().removeOnPreDrawListener(this);

                return true;
            }
        });
    }

}
