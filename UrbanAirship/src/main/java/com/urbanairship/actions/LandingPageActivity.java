/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.widget.LandingPageWebView;
import com.urbanairship.widget.UAWebViewClient;

/**
 * An activity that displays a landing page.
 * <p/>
 * The easiest way to customize the landing page view is to specify a theme
 * for the activity in the AndroidManifest.xml.  A custom layout can be specified
 * by providing a metadata element com.urbanairship.action.LANDING_PAGE_VIEW with
 * the specified view resource.  When supplying a custom view, a
 * {@link com.urbanairship.widget.LandingPageWebView} must be defined with id
 * android.R.id.primary with an optional progress view with id android.R.id.progress.
 * An optional close button can be added by defining it in the layout and setting
 * the android:onClick="onCloseButtonClick".  The onCloseButtonClick method will
 * close the landing page by finishing the activity.
 * <p/>
 * More extensive landing page customization can be defined by creating custom Activity.
 * In the AndroidManifest.xml, define the landing page activity with an intent
 * filter with action com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION,
 * with category android:name="android.intent.category.DEFAULT", and data scheme
 * "http" and "https".  The custom activity is responsible for displaying the url
 * defined in the intent's data field.
 * <p/>
 * A landing page action could be triggered from a notification before any of the
 * other activities are started.  When this happens, the landing page activity will
 * be the root task and up or back navigation will exit the application.  Defining
 * a parent activity will force the parent to be started first if the activity
 * is the root task.  Define the parent activity by supplying the
 * android:parentActivityName attribute in the <activity> element for API >= 16, and
 * supplying a metadata element with a value for android.support.PARENT_ACTIVITY
 * for older api versions.
 */
public class LandingPageActivity extends Activity {

    /**
     * Metadata extra to specify a custom landing page view.
     */
    public static final String LANDING_PAGE_VIEW_KEY = "com.urbanairship.action.LANDING_PAGE_VIEW";

    /**
     * Metadata extra to specify the parent activity for older api versions.
     */
    public static final String PARENT_ACTIVITY_KEY = "android.support.PARENT_ACTIVITY";

    /**
     * Metadata extra to specify the web view's background color when displaying landing pages.
     */
    public static final String LANDING_PAGE_BACKGROUND_COLOR = "com.urbanairship.LANDING_PAGE_BACKGROUND_COLOR";

    private static final long LANDING_PAGE_RETRY_DELAY_MS = 20000; // 20 seconds

    private ActivityInfo info;
    private LandingPageWebView webView;
    private Integer error = null;
    private int webViewBackgroundColor = -1;
    private Handler handler;
    private String url;


    @SuppressLint("NewApi")
    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Autopilot.automaticTakeOff(getApplication());

        Logger.warn("Creating landing page activity.");

        Intent intent = getIntent();

        if (intent == null) {
            Logger.warn("Started Action Activity with null intent");
            finish();
            return;
        }

        info = ManifestUtils.getActivityInfo(getClass());
        Bundle metadata = info.metaData == null ? new Bundle() : info.metaData;

        webViewBackgroundColor = metadata.getInt(LANDING_PAGE_BACKGROUND_COLOR, -1);
        url = getIntent().getDataString();
        handler = new Handler();

        if (UAStringUtil.isEmpty(url)) {
            Logger.warn("No landing page url to load.");
            finish();
            return;
        }

        int customView = metadata.getInt(LANDING_PAGE_VIEW_KEY, -1);
        if (customView != -1) {
            setContentView(customView);
        } else {
            setContentView(createDefaultLandingPageView());
        }

        if (Build.VERSION.SDK_INT >= 11) {
            ActionBar actionBar = getActionBar();
            if (actionBar != null) {
                actionBar.setDisplayOptions(
                        ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
            }
        }

        webView = (LandingPageWebView) findViewById(android.R.id.primary);
        final ProgressBar progressBar = (ProgressBar) findViewById(android.R.id.progress);

        if (webView != null) {
            if (Build.VERSION.SDK_INT >= 12) {
                webView.setAlpha(0);
            } else {
                webView.setVisibility(View.INVISIBLE);
            }

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
                        Logger.error("Failed to load landing page " + failingUrl + " with error " + errorCode + " " + description);
                        error = errorCode;
                    }
                }
            });
        } else {
            Logger.error("A LandingPageWebView with id android.R.id.primary is not defined" +
                    " in the custom layout.  Unable to show the landing page.");
            finish();
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        Logger.info("New intent received for landing page activity");
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

    @Override
    protected void onStart() {
        super.onStart();

        // Activity instrumentation for analytic tracking
        Analytics.activityStarted(this);

        // Start loading the landing page
        loadLandingPage();
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Activity instrumentation for analytic tracking
        Analytics.activityStopped(this);

        // Stop any loading
        webView.stopLoading();

        // Cancel any delayed landing pages
        handler.removeCallbacksAndMessages(url);
    }

    @SuppressLint("NewApi")
    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 11 && webView != null) {
            webView.onResume();
        }
    }

    @SuppressLint("NewApi")
    @Override
    public void onPause() {
        super.onPause();
        if (Build.VERSION.SDK_INT >= 11 && webView != null) {
            webView.onPause();
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
        if (Build.VERSION.SDK_INT < 12) {
            if (in != null) {
                in.setVisibility(View.VISIBLE);
            }

            if (out != null) {
                out.setVisibility(View.GONE);
            }
            return;
        }

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
        LandingPageWebView webView = new LandingPageWebView(this);
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

    /**
     * Loads the landing page url
     */
    protected void loadLandingPage() {
        loadLandingPage(0);
    }

    /**
     * Load the landing page url with a delay
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
            }, url, SystemClock.uptimeMillis() + delay);
            return;
        }

        Logger.info("Loading landing page: " + url);

        // Set the background color
        if (webViewBackgroundColor != -1) {
            webView.setBackgroundColor(webViewBackgroundColor);
        }

        // Use software rendering if available to avoid the white box web view glitch.
        if (Build.VERSION.SDK_INT >= 11) {
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        error = null;
        webView.loadUrl(getIntent().getDataString());
    }

    /**
     * Relaunches the activity.
     *
     * @param uri The URI of the intent.
     * @param extras The extras bundle.
     */
    private void restartActivity(Uri uri, Bundle extras) {
        Logger.info("Relaunching activity");

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
