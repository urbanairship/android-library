/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.html;

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.webkit.WebView;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.widget.UAWebViewClient;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class HtmlWebViewClient extends UAWebViewClient {

    /**
     * Close command to handle close method in the Javascript Interface.
     */
    @NonNull
    public static final String DISMISS_COMMAND = "dismiss";


    public abstract void onMessageDismissed(@NonNull JsonValue argument);

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param webView The web view.
     * @param url The url being loaded.
     * @return <code>true</code> if the url was loaded, otherwise <code>false</code>.
     */
    @Override
    protected boolean interceptUrl(@NonNull WebView webView, @Nullable String url) {
        if (url == null) {
            return false;
        }

        Uri uri = Uri.parse(url);

        if (uri.getHost() == null || !uri.getScheme().equals(UA_ACTION_SCHEME) || !isWhiteListed(webView.getUrl())) {
            return false;
        }

        Logger.verbose("Intercepting: " + url);

        switch (uri.getHost()) {
            case DISMISS_COMMAND:
                String path = uri.getEncodedPath();
                if (path != null) {
                    String[] components = path.split("/");
                    if (components.length > 1) {
                        try {
                            JsonValue value = JsonValue.parseString(Uri.decode(components[1]));
                            onMessageDismissed(value);
                        } catch (JsonException e) {
                            Logger.error("Unable to decode message resolution from JSON.", e);
                        }
                    } else {
                        Logger.error("Unable to decode message resolution, invalid path");
                    }
                } else {
                    Logger.error("Unable to decode message resolution, missing path");
                }

                return true;
            default:
                return super.interceptUrl(webView, url);
        }
    }
}
