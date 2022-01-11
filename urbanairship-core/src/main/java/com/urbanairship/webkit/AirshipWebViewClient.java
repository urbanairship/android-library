/* Copyright Airship and Contributors */

package com.urbanairship.webkit;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.view.KeyEvent;
import android.webkit.HttpAuthHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestExtender;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.javascript.JavaScriptEnvironment;
import com.urbanairship.javascript.JavaScriptExecutor;
import com.urbanairship.javascript.NativeBridge;
import com.urbanairship.js.UrlAllowList;

import java.io.BufferedInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * A web view client that enables the Airship Native Bridge on allowed URLs.
 */
public class AirshipWebViewClient extends WebViewClient {

    private final Map<String, Credentials> authRequestCredentials = new HashMap<>();
    private final Map<WebView, Cancelable> pendingNativeBridgeLoads = new WeakHashMap<>();
    private final NativeBridge nativeBridge;

    private boolean faviconEnabled = false;

    /**
     * WebView client listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Listener {
        void onPageFinished(@NonNull final WebView view, final @Nullable String url);
        void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error);
        boolean onClose(@NonNull WebView view);
    }

    private List<Listener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Default constructor.
     */
    public AirshipWebViewClient() {
        this(new ActionRunRequestFactory());
    }

    public AirshipWebViewClient(@NonNull ActionRunRequestFactory requestFactory) {
        this(new NativeBridge(requestFactory));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    protected AirshipWebViewClient(@NonNull NativeBridge nativeBridge) {
        this.nativeBridge = nativeBridge;
    }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);

        if (view != null && request != null && error != null) {
            for (Listener listener : listeners) {
                listener.onReceivedError(view, request, error);
            }
        }
    }

    /**
     * Called to extend the action request for actions run through the native bridge.
     *
     * @param request The request
     * @param webView The web view.
     * @return The action run request.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected ActionRunRequest extendActionRequest(@NonNull ActionRunRequest request, @NonNull WebView webView) {
        return request;
    }

    /**
     * Called to extend the JavaScript environment.
     *
     * @param builder The environment builder.
     * @param webView The web view.
     * @return The builder.
     * @hide
     */
    @CallSuper
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected JavaScriptEnvironment.Builder extendJavascriptEnvironment(@NonNull JavaScriptEnvironment.Builder builder, @NonNull WebView webView) {
        return builder.addGetter("getDeviceModel", Build.MODEL)
                      .addGetter("getChannelId", UAirship.shared().getChannel().getId())
                      .addGetter("getAppKey", UAirship.shared().getAirshipConfigOptions().appKey)
                      .addGetter("getNamedUser", UAirship.shared().getContact().getNamedUserId());
    }

    /**
     * Called when a custom uairship:// command is intercepted.
     *
     * @param webView The web view.
     * @param command The command (or host).
     * @param uri The full uri in the shape of `uairship://<COMMAND>*`.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onAirshipCommand(@NonNull WebView webView, @NonNull String command, @NonNull Uri uri) {
    }

    /**
     * Called when UAirship.close() is triggered from the Airship Javascript interface.
     *
     * The default behavior simulates a back key press.
     *
     * @param webView The web view.
     */
    protected void onClose(@NonNull WebView webView) {
        boolean handled = false;
        for (Listener listener : listeners) {
            handled = handled || listener.onClose(webView);
        }

        if (!handled) {
            webView.getRootView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            webView.getRootView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
        }
    }

    /**
     * Sets the action completion callback to be invoked whenever an {@link com.urbanairship.actions.Action}
     * is finished running from the web view.
     *
     * @param actionCompletionCallback The completion callback.
     */
    public void setActionCompletionCallback(@Nullable ActionCompletionCallback actionCompletionCallback) {
        nativeBridge.setActionCompletionCallback(actionCompletionCallback);
    }

    @CallSuper
    @Override
    public boolean shouldOverrideUrlLoading(@NonNull WebView webView, @Nullable String url) {
        if (interceptUrl(webView, url)) {
            return true;
        }

        return super.shouldOverrideUrlLoading(webView, url);
    }

    /**
     * Sets favicon enabled flag.
     *
     * @param enabled {@code true} to enable favicon, {@code false} to disable and intercept favicon request.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void setFaviconEnabled(boolean enabled) {
        faviconEnabled = enabled;
    }

    /**
     * Intercepts the favicon request and returns a blank favicon
     *
     * @param webView The web view.
     * @param url The url being loaded.
     * @return The blank favicon image embedded in a WebResourceResponse or null if the url does not contain a favicon.
     */
    @CallSuper
    @Override
    @Nullable
    public WebResourceResponse shouldInterceptRequest(@NonNull WebView webView, @NonNull String url) {
        if (faviconEnabled) {
            return null;
        }

        if (url.toLowerCase().endsWith("/favicon.ico")) {
            return generateEmptyFaviconResponse(webView);
        }

        return null;
    }

    /**
     * Intercepts the favicon request and returns blank favicon
     *
     * @param webView The web view.
     * @param request The WebResourceRequest being loaded.
     * @return The blank favicon image embedded in a WebResourceResponse or null if the url does not contain a favicon.
     */
    @CallSuper
    @Override
    @SuppressLint("NewApi")
    @Nullable
    public WebResourceResponse shouldInterceptRequest(@NonNull WebView webView, @NonNull WebResourceRequest request) {
        if (faviconEnabled) {
            return super.shouldInterceptRequest(webView, request);
        }

        if (!request.isForMainFrame()) {
            String path = request.getUrl().getPath();
            if (path != null && path.endsWith("/favicon.ico")) {
                return generateEmptyFaviconResponse(webView);
            }
        }

        return super.shouldInterceptRequest(webView, request);
    }

    @CallSuper
    @Override
    public void onLoadResource(@NonNull WebView webView, @Nullable String url) {
        /*
         * Sometimes shouldOverrideUrlLoading is not called when the uairship library is ready for whatever reasons,
         * but once shouldOverrideUrlLoading is called and returns true it will prevent onLoadResource from
         * being called with the url.
         */
        interceptUrl(webView, url);
    }

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param webView The web view.
     * @param url The url being loaded.
     * @return <code>true</code> if the url was loaded, otherwise <code>false</code>.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    private boolean interceptUrl(@NonNull final WebView webView, @Nullable String url) {
        if (!isAllowed(webView.getUrl())) {
            return false;
        }

        JavaScriptExecutor javaScriptExecutor = new WebViewJavaScriptExecutor(webView);

        ActionRunRequestExtender extender = new ActionRunRequestExtender() {
            @NonNull
            @Override
            public ActionRunRequest extend(@NonNull ActionRunRequest request) {
                return AirshipWebViewClient.this.extendActionRequest(request, webView);
            }
        };

        NativeBridge.CommandDelegate delegate = new NativeBridge.CommandDelegate() {
            @Override
            public void onClose() {
                AirshipWebViewClient.this.onClose(webView);
            }

            @Override
            public void onAirshipCommand(@NonNull String command, @NonNull Uri uri) {
                AirshipWebViewClient.this.onAirshipCommand(webView, command, uri);
            }
        };

        return nativeBridge.onHandleCommand(url, javaScriptExecutor, extender, delegate);
    }

    private WebResourceResponse generateEmptyFaviconResponse(@NonNull WebView webView) {
        try {
            return new WebResourceResponse("image/png", null, new BufferedInputStream(webView.getContext().getResources().openRawResource(R.raw.ua_blank_favicon)));
        } catch (Exception e) {
            Logger.error(e, "Failed to read blank favicon with IOException.");
        }

        return null;
    }

    @Override
    @CallSuper
    public void onPageFinished(@Nullable final WebView view, final @Nullable String url) {
        if (view == null) {
            return;
        }

        for (Listener listener : listeners) {
            listener.onPageFinished(view, url);
        }

        if (!isAllowed(url)) {
            Logger.debug("%s is not an allowed URL. Airship Javascript interface will not be accessible.", url);
            return;
        }

        JavaScriptEnvironment.Builder environment = extendJavascriptEnvironment(JavaScriptEnvironment.newBuilder(), view);

        Cancelable cancelable = nativeBridge.loadJavaScriptEnvironment(
                view.getContext(),
                environment.build(),
                new WebViewJavaScriptExecutor(view));

        pendingNativeBridgeLoads.put(view, cancelable);
    }

    @CallSuper
    @Override
    public void onPageStarted(@NonNull WebView view, @Nullable String url, @Nullable Bitmap favicon) {
        Cancelable cancelable = pendingNativeBridgeLoads.get(view);
        if (cancelable != null) {
            cancelable.cancel();
        }
    }

    /**
     * Checks if the URL is allowed.
     *
     * @param url The URL being loaded.
     * @return <code>true</code> if the URL is allowed, otherwise <code>false</code>.
     */
    protected boolean isAllowed(@Nullable String url) {
        return UAirship.shared().getUrlAllowList().isAllowed(url, UrlAllowList.SCOPE_JAVASCRIPT_INTERFACE);
    }

    @CallSuper
    @Override
    public void onReceivedHttpAuthRequest(@NonNull WebView view, @NonNull HttpAuthHandler handler, @Nullable String host,
                                          @Nullable String realm) {
        Credentials credentials = authRequestCredentials.get(host);
        if (credentials != null) {
            handler.proceed(credentials.username, credentials.password);
        }
    }

    /**
     * Adds auth request credentials for a host.
     *
     * @param expectedAuthHost The expected host.
     * @param username The auth user.
     * @param password The auth password.
     */
    public void addAuthRequestCredentials(@NonNull String expectedAuthHost, @Nullable String username, @Nullable String password) {
        authRequestCredentials.put(expectedAuthHost, new Credentials(username, password));
    }

    /**
     * Removes auth request credentials for a host.
     *
     * @param expectedAuthHost The expected host.
     */
    public void removeAuthRequestCredentials(@NonNull String expectedAuthHost) {
        authRequestCredentials.remove(expectedAuthHost);
    }

    /**
     * Adds a listener.
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addListener(@NonNull Listener listener) {
        this.listeners.add(listener);
    }

    /**
     * Removes a listener.
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void removeListener(@NonNull Listener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Credentials model class.
     */
    private static class Credentials {

        final String username;
        final String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }

    }

}
