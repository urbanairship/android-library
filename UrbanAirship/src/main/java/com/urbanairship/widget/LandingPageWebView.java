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
import android.os.Build;
import android.util.AttributeSet;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.UAirship;

import org.apache.http.Header;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.auth.BasicScheme;

import java.util.HashMap;
import java.util.Map;

/**
 * A web view configured to display a Landing Page.
 * <p/>
 * Only available in API 5 and higher (Eclair)
 */
public class LandingPageWebView extends UAWebView {

    /**
     * Construct a LandingPageWebView.
     *
     * @param context A Context object used to access application assets.
     */
    public LandingPageWebView(Context context) {
        super(context);
    }

    /**
     * Construct a LandingPageWebView.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     */
    public LandingPageWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }


    /**
     * Construct a LandingPageWebView.
     *
     * @param context A Context object used to access application assets.
     * @param attrs An AttributeSet passed to our parent.
     * @param defStyle The default style resource id.
     */
    public LandingPageWebView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Loads the given URL.
     *
     * @param url The URL to load.
     */
    @Override
    @SuppressLint("NewApi")
    public void loadUrl(String url) {
        // Not a landing page content url, load url normally
        if (url == null || !url.startsWith(UAirship.shared().getAirshipConfigOptions().landingPageContentURL)) {
            super.loadUrl(url);
            return;
        }

        // Do pre auth if we can
        if (Build.VERSION.SDK_INT >= 8) {
            AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(options.getAppKey(), options.getAppSecret());
            Header credentialHeader = BasicScheme.authenticate(credentials, "UTF-8", false);

            HashMap<String, String> headers = new HashMap<>();
            headers.put(credentialHeader.getName(), credentialHeader.getValue());

            super.loadUrl(url, headers);
        } else {
            super.loadUrl(url);
        }

        // Set the client auth request
        setClientAuthRequest(url);
    }

    /**
     * Loads the given URL with the specified additional HTTP headers.
     *
     * @param url The URL to load.
     * @param additionalHttpHeaders The additional headers to be used in the HTTP request for
     * this URL.
     */
    @Override
    @TargetApi(Build.VERSION_CODES.FROYO)
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        super.loadUrl(url, additionalHttpHeaders);

        if (url != null && url.startsWith(UAirship.shared().getAirshipConfigOptions().landingPageContentURL)) {
            setClientAuthRequest(url);
        }
    }

    /**
     * Set the client authorization request.
     *
     * @param url The URL string.
     */
    private void setClientAuthRequest(String url) {
        AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();
        setClientAuthRequest(url, options.getAppKey(), options.getAppSecret());
    }

}
