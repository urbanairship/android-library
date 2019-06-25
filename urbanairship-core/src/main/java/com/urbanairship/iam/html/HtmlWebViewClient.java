/* Copyright Airship and Contributors */

package com.urbanairship.iam.html;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import android.webkit.WebView;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.widget.UAWebViewClient;

/**
 * A version of the {@link UAWebViewClient} for HTML in-app messages, which adds a command
 * for dismissing the message with resolution info represented as URL-encoded JSON.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class HtmlWebViewClient extends UAWebViewClient {

    /**
     * Close command to handle close method in the Javascript Interface.
     */
    @NonNull
    public static final String DISMISS_COMMAND = "dismiss";

    /**
     * Default constructor.
     */
    public HtmlWebViewClient() {
        super();
    }

    /**
     * Constructs an HtmlWebViewClient with the specified ActionRunRequestFactory.
     *
     * @param actionRunRequestFactory The action run request factory.
     */
    protected HtmlWebViewClient(ActionRunRequestFactory actionRunRequestFactory) {
        super(actionRunRequestFactory);
    }

    /**
     * Called when the dismiss command is invoked from the native bridge. Override to
     * customize the handling of this event.
     *
     * @param argument The argument data passed in the dismiss call.
     */
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

        if (uri.getHost() == null || !UA_ACTION_SCHEME.equals(uri.getScheme()) || !isWhiteListed(webView.getUrl())) {
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
