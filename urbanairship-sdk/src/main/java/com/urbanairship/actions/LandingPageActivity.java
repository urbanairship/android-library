/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.urbanairship.Autopilot;
import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.widget.UAWebView;
import com.urbanairship.widget.UAWebViewClient;

import java.lang.ref.WeakReference;

/**
 * An activity that displays a landing page.
 * <p/>
 * The easiest way to customize the landing page view is to specify a theme
 * for the activity in the AndroidManifest.xml. A custom layout can be specified
 * by providing a metadata element com.urbanairship.action.LANDING_PAGE_VIEW with
 * the specified view resource. When supplying a custom view, a
 * {@link com.urbanairship.widget.UAWebView} must be defined with id
 * android.R.id.primary inside a content holder with id android.R.id.content_holder.
 * An optional progress view with id android.R.id.progress can be added to the webview.
 * An optional close button can be added by defining it in the layout and setting
 * the android:onClick="onCloseButtonClick". The onCloseButtonClick method will
 * close the landing page by finishing the activity.
 * <p/>
 * More extensive landing page customization can be defined by creating custom Activity.
 * In the AndroidManifest.xml, define the landing page activity with an intent
 * filter with action com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION,
 * with category android:name="android.intent.category.DEFAULT", and data scheme
 * "http", "https" and "message". The "message" scheme is used to display a {@link RichPushMessage}
 * in the landing page. The message's ID is available as the {@link Uri#getSchemeSpecificPart()}.
 */
public class LandingPageActivity extends Activity {

    /**
     * Metadata extra to specify a custom landing page view.
     */
    public static final String LANDING_PAGE_VIEW_KEY = "com.urbanairship.action.LANDING_PAGE_VIEW";

    /**
     * The content's width payload key
     */
    public static final String WIDTH_KEY = "width";

    /**
     * The content's height payload key
     */
    public static final String HEIGHT_KEY = "height";

    /**
     * The content's aspectLock payload key
     */
    public static final String ASPECT_LOCK_KEY = "aspectLock";

    /**
     * Metadata extra to specify the web view's background color when displaying landing pages.
     */
    public static final String LANDING_PAGE_BACKGROUND_COLOR = "com.urbanairship.LANDING_PAGE_BACKGROUND_COLOR";

    private static final long LANDING_PAGE_RETRY_DELAY_MS = 20000; // 20 seconds

    private UAWebView webView;
    private Integer error = null;
    private int webViewBackgroundColor = -1;
    private Handler handler;
    private Uri uri;
    private int width;
    private int height;
    private boolean aspectLock;
    private Cancelable fetchMessagesCallback;

    @SuppressLint("NewApi")
    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        if (!UAirship.isTakingOff() && !UAirship.isFlying()) {
            Logger.error("LandingPageActivity - unable to create activity, takeOff not called.");
            finish();
            return;
        }

        Logger.debug("Creating landing page activity.");

        Intent intent = getIntent();

        if (intent == null) {
            Logger.warn("LandingPageActivity - Started activity with null intent");
            finish();
            return;
        }

        ActivityInfo info = ManifestUtils.getActivityInfo(getClass());
        Bundle metadata = info == null || info.metaData == null ? new Bundle() : info.metaData;

        webViewBackgroundColor = metadata.getInt(LANDING_PAGE_BACKGROUND_COLOR, -1);
        handler = new Handler();
        uri = intent.getData();


        this.height = intent.getIntExtra(HEIGHT_KEY, 0);
        this.width = intent.getIntExtra(WIDTH_KEY, 0);
        this.aspectLock = intent.getBooleanExtra(ASPECT_LOCK_KEY, false) && height != 0 && width != 0;

        if (uri == null) {
            Logger.warn("LandingPageActivity - No landing page uri to load.");
            finish();
            return;
        }

        int customView = metadata.getInt(LANDING_PAGE_VIEW_KEY, -1);
        if (customView != -1) {
            setContentView(customView);
        } else {
            setContentView(createDefaultLandingPageView());
        }

        applySizeConstraints();

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(
                    ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }

        webView = (UAWebView) findViewById(android.R.id.primary);
        final ProgressBar progressBar = (ProgressBar) findViewById(android.R.id.progress);

        if (webView == null) {
            Logger.error("LandingPageActivity - A UAWebView with id android.R.id.primary is not defined" +
                    " in the custom layout.  Unable to show the landing page.");
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
                            loadLandingPage(LANDING_PAGE_RETRY_DELAY_MS);
                            break;
                        default:
                            // Load an empty page
                            error = null;
                            webView.loadData("", "text/html", null);
                    }
                } else {
                    // Set the background color again, fixes some older API versions
                    if (webViewBackgroundColor != -1) {
                        webView.setBackgroundColor(webViewBackgroundColor);
                    }
                    crossFade(webView, progressBar);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.equals(getIntent().getDataString())) {
                    Logger.error("LandingPageActivity - Failed to load page " + failingUrl + " with error " + errorCode + " " + description);
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

    }

    @Override
    public void onNewIntent(Intent intent) {
        Logger.debug("LandingPageActivity - New intent received for landing page");
        restartActivity(intent.getData(), intent.getExtras());
    }

    /**
     * Determines if <code>android.R.id.home</code> was selected.
     *
     * @param item The menu item that was selected.
     * @return Return <code>true</code> if <code>android.R.id.home</code> was selected,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();

        // Start loading the landing page
        loadLandingPage();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
        webView.onPause();

        // Stop any loading
        webView.stopLoading();

        // Cancel any delayed landing pages
        handler.removeCallbacksAndMessages(uri);

        if (fetchMessagesCallback != null) {
            fetchMessagesCallback.cancel();
            fetchMessagesCallback = null;
        }
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
     * Finishes the activity.
     *
     * @param view The view that was clicked.
     */
    public void onCloseButtonClick(View view) {
        this.finish();
    }

    /**
     * Creates the default landing page view
     *
     * @return A landing page view
     */
    private View createDefaultLandingPageView() {
        FrameLayout frameLayout = new FrameLayout(this);
        UAWebView webView = new UAWebView(this);
        webView.setId(android.R.id.primary);

        FrameLayout.LayoutParams webViewLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        webViewLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(webView, webViewLayoutParams);

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setId(android.R.id.progress);

        FrameLayout.LayoutParams progressBarLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressBarLayoutParams.gravity = Gravity.CENTER;

        frameLayout.addView(progressBar, progressBarLayoutParams);

        return frameLayout;
    }

    public void applySizeConstraints() {
        if (width == 0 && height == 0) {
            return;
        }

        View view = findViewById(R.id.content_holder);
        if (view == null) {
            return;
        }

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

    /**
     * Loads the landing page uri
     */
    protected void loadLandingPage() {
        loadLandingPage(0);
    }

    /**
     * Load the landing page uri with a delay
     *
     * @param delay Delay before loading the landing page.  Delay of 0 or less
     * will start loading the landing page immediately.
     */
    @SuppressLint("NewApi")
    protected void loadLandingPage(long delay) {
        if (webView == null) {
            return;
        }

        webView.stopLoading();

        if (delay > 0) {
            handler.postAtTime(new Runnable() {
                @Override
                public void run() {
                    loadLandingPage(0);
                }
            }, uri, SystemClock.uptimeMillis() + delay);
            return;
        }

        Logger.info("Loading landing page: " + uri);

        // Set the background color
        if (webViewBackgroundColor != -1) {
            webView.setBackgroundColor(webViewBackgroundColor);
        }

        error = null;

        if (uri.getScheme().equalsIgnoreCase(RichPushInbox.MESSAGE_DATA_SCHEME)) {
            final String messageId = uri.getSchemeSpecificPart();
            RichPushMessage message = UAirship.shared()
                                              .getInbox()
                                              .getMessage(messageId);
            if (message != null) {
                webView.loadRichPushMessage(message);
                message.markRead();
            } else {
                fetchMessagesCallback = UAirship.shared().getInbox().fetchMessages(new RichPushInbox.FetchMessagesCallback() {
                    @Override
                    public void onFinished(boolean success) {
                        if (success && UAirship.shared().getInbox().getMessage(messageId) == null) {
                            Logger.error("Message " + messageId + " not found.");
                            finish();
                        }

                        loadLandingPage();
                    }
                });


            }
        } else {
            webView.loadUrl(uri.toString());
        }
    }

    /**
     * Relaunches the activity.
     *
     * @param uri The URI of the intent.
     * @param extras The extras bundle.
     */
    private void restartActivity(Uri uri, Bundle extras) {
        Logger.debug("Relaunching activity");

        finish();

        Intent restartIntent = new Intent()
                .setClass(this, this.getClass())
                .setData(uri)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (extras != null) {
            restartIntent.putExtras(extras);
        }

        this.startActivity(restartIntent);
    }
}
