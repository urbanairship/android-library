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

import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.UriUtils;
import com.urbanairship.widget.UAWebView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;


/**
 * Action for launching a Landing Page.
 * <p/>
 * The landing page will not be launched in Situation.PUSH_RECEIVED, instead it will be cached
 * if the action is triggered with a payload that sets "cache_on_receive" to true.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.PUSH_RECEIVED, Situation.WEB_VIEW_INVOCATION,
 * Situation.MANUAL_INVOCATION, and Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value types: URL defined as either String, {@link android.net.Uri}, {@link android.net.Uri}
 * or a Map containing the key "url" that defines the URL. The map argument value can also define a
 * "cache_on_receive" flag to enable or disable caching when a PUSH_RECEIVED. Caching is disabled
 * by default.
 * <p/>
 * <pre>{@code Note: URLs in the format of "u:<content-id>" will be treated as a short url and
 * used to construct a separate url using the content id. }</pre>
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^p, landing_page_action
 * <p/>
 * Default Registration Predicate: Rejects Situation.PUSH_RECEIVED if the application
 * has not been opened in the last week.
 */
public class LandingPageAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "landing_page_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^p";

    /**
     * Intent action for showing a URL in a {@link com.urbanairship.widget.UAWebView}
     */
    public static final String SHOW_LANDING_PAGE_INTENT_ACTION = "com.urbanairship.actions.SHOW_LANDING_PAGE_INTENT_ACTION";

    /**
     * The content's url payload key
     */
    public static final String URL_KEY = "url";

    /**
     * The payload key for indicating if the landing page should be cached
     * when triggered in Situation.PUSH_RECEIVED
     */
    public static final String CACHE_ON_RECEIVE_KEY = "cache_on_receive";

    /**
     * Performs the landing page action.
     *
     * @param actionName The name of the action from the registry.
     * @param arguments The action arguments.
     * @return The result of the action.
     */
    @Override
    public ActionResult perform(String actionName, ActionArguments arguments) {
        final Uri uri = parseUri(arguments);

        switch (arguments.getSituation()) {
            case PUSH_RECEIVED:
                if (shouldCacheOnReceive(arguments)) {
                    // Cache the landing page by loading the url in a web view
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.postAtFrontOfQueue(new Runnable() {
                        @Override
                        public void run() {
                            UAWebView webView = new UAWebView(UAirship.getApplicationContext());
                            webView.loadUrl(uri.toString());
                        }
                    });

                }
                break;
            default:
                final Intent actionIntent = new Intent(SHOW_LANDING_PAGE_INTENT_ACTION, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .setPackage(UAirship.getPackageName());


                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        UAirship.getApplicationContext().startActivity(actionIntent);
                    }
                });
        }

        return ActionResult.newEmptyResult();
    }

    /**
     * Checks if the argument's value can be parsed to a URI and if the situation is not
     * Situation.PUSH_RECEIVED.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        if (!super.acceptsArguments(arguments)) {
            return false;
        }

        switch (arguments.getSituation()) {
            case PUSH_OPENED:
            case PUSH_RECEIVED:
            case WEB_VIEW_INVOCATION:
            case MANUAL_INVOCATION:
            case FOREGROUND_NOTIFICATION_ACTION_BUTTON:
                return parseUri(arguments) != null;
            default:
                return false;
        }
    }

    /**
     * Parses the ActionArguments for a landing page URI.
     *
     * @param arguments The action arguments.
     * @return A landing page Uri, or null if the arguments could not be parsed.
     */
    protected Uri parseUri(ActionArguments arguments) {
        Object uriValue;

        if (arguments.getValue() instanceof Map) {
            Map map = (Map) arguments.getValue();
            uriValue = map.get(URL_KEY);
        } else {
            uriValue = arguments.getValue();
        }

        if (uriValue == null) {
            return null;
        }

        // Assume a string
        Uri uri = UriUtils.parse(uriValue);
        if (UAStringUtil.isEmpty(uri.toString())) {
            return null;
        }

        if ("u".equals(uri.getScheme())) {
            String id;
            try {
                id = URLEncoder.encode(uri.getSchemeSpecificPart(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Logger.warn("Unable to decode " + uri.getSchemeSpecificPart());
                return null;
            }

            AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();
            uri = Uri.parse(options.landingPageContentURL + options.getAppKey() + "/" + id);
        }

        // Add https scheme if not set
        if (UAStringUtil.isEmpty(uri.getScheme())) {
            uri = Uri.parse("https://" + uri.toString());
        }

        return uri;
    }

    /**
     * Checks if the landing page arguments define whether the landing page
     * should cache on receive.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the argument's value contains a payload
     * with CACHE_ON_RECEIVE_KEY set to true, otherwise <code>false</code>.
     */
    protected boolean shouldCacheOnReceive(ActionArguments arguments) {
        if (arguments.getValue() instanceof Map) {
            Map map = (Map) arguments.getValue();
            Object cache = map.get(CACHE_ON_RECEIVE_KEY);

            if (cache != null && cache instanceof Boolean) {
                return ((Boolean) cache).booleanValue();
            }
        }

        return false;
    }
}

