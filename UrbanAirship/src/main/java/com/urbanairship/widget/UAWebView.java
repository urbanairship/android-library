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
package com.urbanairship.widget;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import java.io.File;
import java.util.Map;

/**
 * A web view that sets settings appropriate for Urban Airship content.
 * <p/>
 * Only available in API 5 and higher (Eclair)
 */
@TargetApi(5)
public class UAWebView extends WebView {

    private WebViewClient webViewClient;

    private static final String CACHE_DIRECTORY = "urbanairship";

    private String currentClientAuthRequestUrl;

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     */
    public UAWebView(Context context) {
        super(context);
        if (!isInEditMode()) {
            init();
        }
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public UAWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        if (!isInEditMode()) {
            init();
        }
    }

    /**
     * UAWebView Constructor
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource id.
     */
    public UAWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (!isInEditMode()) {
            init();
        }
    }

    /**
     * Helper method that sets the default web view settings for urban airship
     * and calls through to initializeView and populateCustomJavascriptInterfaces.
     */
    @SuppressLint({ "NewApi", "SetJavaScriptEnabled" })
    void init() {
        WebSettings settings = getSettings();

        if (Build.VERSION.SDK_INT >= 7) {
            settings.setAppCacheEnabled(true);
            settings.setAppCachePath(getCachePath());
            settings.setDomStorageEnabled(true);
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
        super.loadUrl(url);
    }

    /**
     * Loads the given URL with the specified additional HTTP headers.
     *
     * @param url The URL to load.
     * @param additionalHttpHeaders The additional headers to be used in the HTTP request for
     * this URL.
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        onPreLoad();
        super.loadUrl(url, additionalHttpHeaders);
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
    private void onPreLoad() {
        if (getWebViewClient() == null) {
            Logger.info("No web view client set, setting a default " +
                    "UAWebViewClient for landing page view.");
            setWebViewClient(new UAWebViewClient());
        }

        // Clear the last set auth request
        if (currentClientAuthRequestUrl != null && getWebViewClient() != null && getWebViewClient() instanceof UAWebViewClient) {
            UAWebViewClient webViewClient = (UAWebViewClient) getWebViewClient();
            webViewClient.removeAuthRequestCredentials(currentClientAuthRequestUrl);
            currentClientAuthRequestUrl = null;
        }
    }

    /**
     * Gets the web view client.
     *
     * @return The web view client.
     */
    WebViewClient getWebViewClient() {
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
    void setClientAuthRequest(String url, String username, String password) {
        if (url == null) {
            return;
        }

        currentClientAuthRequestUrl = url;

        if (getWebViewClient() != null && getWebViewClient() instanceof UAWebViewClient) {
            UAWebViewClient webViewClient = (UAWebViewClient) getWebViewClient();

            String host = Uri.parse(url).getHost();
            webViewClient.addAuthRequestCredentials(host, username, password);
        }
    }

}
