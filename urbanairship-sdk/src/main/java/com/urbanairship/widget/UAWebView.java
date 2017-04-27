/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Base64;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.richpush.RichPushUser;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A web view that sets settings appropriate for Urban Airship content.
 */
public class UAWebView extends WebView {

    private WebViewClient webViewClient;

    private static final String CACHE_DIRECTORY = "urbanairship";

    private String currentClientAuthRequestUrl;
    private RichPushMessage currentMessage;

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     */
    public UAWebView(Context context) {
        this(context, null);
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public UAWebView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.webViewStyle);
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     */
    public UAWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        if (!isInEditMode()) {
            init(context, attrs, defStyle, 0);
        }
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or can not be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public UAWebView(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        super(context, attrs, defStyle, defResStyle);

        if (!isInEditMode()) {
            init(context, attrs, defStyle, defResStyle);
        }
    }

    /**
     * Helper method that sets the default web view settings for urban airship
     * and calls through to initializeView and populateCustomJavascriptInterfaces.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource ID.
     * @param defResStyle A resource identifier of a style resource that supplies default values for
     * the view, used only if defStyle is 0 or can not be found in the theme. Can be 0 to not
     * look for defaults.
     */
    @SuppressLint({ "NewApi", "SetJavaScriptEnabled" })
    private void init(Context context, AttributeSet attrs, int defStyle, int defResStyle) {
        WebSettings settings = getSettings();

        settings.setAppCacheEnabled(true);
        settings.setAppCachePath(getCachePath());
        settings.setDomStorageEnabled(true);

        if (Build.VERSION.SDK_INT >= 21) {
            if (attrs != null) {
                TypedArray a = context.getTheme().obtainStyledAttributes(attrs, R.styleable.UAWebView, defStyle, defResStyle);
                try {
                    int mixedContentMode = a.getInteger(R.styleable.UAWebView_mixed_content_mode, WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
                    settings.setMixedContentMode(mixedContentMode);
                } finally {
                    a.recycle();
                }
            }
        }

        settings.setAllowFileAccess(true);
        settings.setJavaScriptEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        initializeView();
        populateCustomJavascriptInterfaces();
    }

    /**
     * Initializes the web view with any default settings.
     * <p/>
     * Called during create.
     */
    protected void initializeView() {}

    /**
     * Populate any custom javascript interfaces by calling
     * addJavascriptInterface(Object interface, String identifier) for each
     * custom interface.
     * <p/>
     * Called after initializeView.
     */
    protected void populateCustomJavascriptInterfaces() {}

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        // Android JB bug where it logs errors incorrectly in a view pager
        // http://stackoverflow.com/questions/12090899/android-webview-jellybean-should-not-happen-no-rect-based-test-nodes-found
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int y = this.getScrollY();
            int x = this.getScrollX();
            this.onScrollChanged(x, y, x, y);
        }

        return super.onTouchEvent(event);
    }

    @Override
    public void loadData(String data, String mimeType, String encoding) {
        onPreLoad();
        super.loadData(data, mimeType, encoding);
    }

    @Override
    public void loadDataWithBaseURL(String baseUrl, String data, String mimeType, String encoding,
                                    String historyUrl) {
        onPreLoad();
        super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl);
    }

    /**
     * Loads the given URL.
     *
     * @param url The URL of the resource to load.
     */
    public void loadUrl(String url) {
        onPreLoad();

        // Add auth to landing page content URLs
        if (url != null && url.startsWith(UAirship.shared().getAirshipConfigOptions().landingPageContentURL)) {
            // Do pre auth if we can
            AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();
            HashMap<String, String> headers = new HashMap<>();
            headers.put("Authorization", createBasicAuth(options.getAppKey(), options.getAppSecret()));

            super.loadUrl(url, headers);

            // Set the client auth request
            setClientAuthRequest(url, options.getAppKey(), options.getAppSecret());
        } else {
            super.loadUrl(url);
        }
    }

    /**
     * Loads the given URL with the specified additional HTTP headers.
     *
     * @param url The URL to load.
     * @param additionalHttpHeaders The additional headers to be used in the HTTP request for
     * this URL.
     */
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        onPreLoad();
        super.loadUrl(url, additionalHttpHeaders);

        if (url != null && url.startsWith(UAirship.shared().getAirshipConfigOptions().landingPageContentURL)) {
            AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();
            setClientAuthRequest(url, options.getAppKey(), options.getAppSecret());
        }
    }

    /**
     * Loads the web view with the rich push message.
     *
     * @param message The RichPushMessage that will be displayed.
     */
    @SuppressLint("NewApi")
    public void loadRichPushMessage(RichPushMessage message) {
        if (message == null) {
            Logger.warn("Unable to load null message into UAWebView");
            return;
        }

        RichPushUser user = UAirship.shared().getInbox().getUser();

        // Send authorization in the headers if the web view supports it
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", createBasicAuth(user.getId(), user.getPassword()));

        loadUrl(message.getMessageBodyUrl(), headers);

        currentMessage = message;

        // Set the auth
        setClientAuthRequest(message.getMessageBodyUrl(), user.getId(), user.getPassword());
    }

    /**
     * The current loaded RichPushMessage.
     *
     * @return The current RichPushMessage that was loaded.
     */
    public RichPushMessage getCurrentMessage() {
        return currentMessage;
    }

    @Override
    public void setWebViewClient(WebViewClient webViewClient) {
        if (!(webViewClient instanceof UAWebViewClient)) {
            Logger.warn("The web view client should extend UAWebViewClient to " +
                    "support urban airship url overrides and triggering actions from.");
        }

        this.webViewClient = webViewClient;
        super.setWebViewClient(webViewClient);
    }

    /**
     * Called right before data or a URL is passed to the web view to be loaded.
     */
    @SuppressLint("NewApi")
    private void onPreLoad() {
        currentMessage = null;

        if (getWebViewClientCompat() == null) {
            Logger.info("No web view client set, setting a default " +
                    "UAWebViewClient for landing page view.");
            setWebViewClient(new UAWebViewClient());
        }

        // Clear the last set auth request
        if (currentClientAuthRequestUrl != null && getWebViewClientCompat() != null && getWebViewClientCompat() instanceof UAWebViewClient) {
            UAWebViewClient webViewClient = (UAWebViewClient) getWebViewClientCompat();
            webViewClient.removeAuthRequestCredentials(currentClientAuthRequestUrl);
            currentClientAuthRequestUrl = null;
        }
    }

    /**
     * Gets the web view client.
     *
     * @return The web view client.
     */
    WebViewClient getWebViewClientCompat() {
        return webViewClient;
    }

    /**
     * Gets the cache directory path. Creates the directories if
     * it does not exist.
     *
     * @return The absolute path to the cache directory.
     */
    private String getCachePath() {
        File cacheDirectory = new File(UAirship.getApplicationContext().getCacheDir(), CACHE_DIRECTORY);
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs();
        }

        return cacheDirectory.getAbsolutePath();
    }

    /**
     * Set the client authorization request.
     *
     * @param url The URL string.
     * @param username The auth user
     * @param password The auth password
     */
    private void setClientAuthRequest(String url, String username, String password) {
        if (url == null) {
            return;
        }

        currentClientAuthRequestUrl = url;

        if (getWebViewClientCompat() != null && getWebViewClientCompat() instanceof UAWebViewClient) {
            UAWebViewClient webViewClient = (UAWebViewClient) getWebViewClientCompat();

            String host = Uri.parse(url).getHost();
            webViewClient.addAuthRequestCredentials(host, username, password);
        }
    }

    /**
     * Creates a basic auth string.
     *
     * @param userName The user name.
     * @param password The password.
     * @return The basic auth string.
     */
    private String createBasicAuth(String userName, String password) {
        String credentials = userName + ":" + password;
        return "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);
    }
}
