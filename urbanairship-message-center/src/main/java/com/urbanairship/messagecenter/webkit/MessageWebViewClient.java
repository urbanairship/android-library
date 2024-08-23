/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.webkit;

import android.os.Bundle;
import android.webkit.WebView;

import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.javascript.JavaScriptEnvironment;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.webkit.AirshipWebViewClient;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * A web view client that enables the Airship Native Bridge for Message Center.
 */
public class MessageWebViewClient extends AirshipWebViewClient {

    @NonNull
    private static SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);

    static {
        DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    /**
     * Default constructor.
     */
    public MessageWebViewClient() {
    }

    /**
     * @hide
     */
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected ActionRunRequest extendActionRequest(@NonNull ActionRunRequest request, @NonNull WebView webView) {
        Bundle metadata = new Bundle();
        Message message = getMessage(webView);
        if (message != null) {
            metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.getMessageId());
        }

        request.setMetadata(metadata);
        return request;
    }

    /**
     * @hide
     */
    @CallSuper
    @NonNull
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected JavaScriptEnvironment.Builder extendJavascriptEnvironment(@NonNull JavaScriptEnvironment.Builder builder, @NonNull WebView webView) {
        final Message message = getMessage(webView);

        JsonMap extras = JsonMap.EMPTY_MAP;
        if (message != null) {
            extras = JsonValue.wrapOpt(message.getExtrasMap()).optMap();
        }

        return super.extendJavascriptEnvironment(builder,webView)
                    .addGetter("getMessageSentDateMS", (message != null) ? message.getSentDateMS() : -1)
                    .addGetter("getMessageId", (message != null) ? message.getMessageId() : null)
                    .addGetter("getMessageTitle", (message != null) ? message.getTitle() : null)
                    .addGetter("getMessageSentDate", (message != null) ? DATE_FORMATTER.format(message.getSentDate()) : null)
                    .addGetter("getUserId", MessageCenter.shared().getUser().getId())
                    .addGetter("getMessageExtras", extras);


    }

    /**
     * Helper method to get the RichPushMessage from the web view.
     *
     * @param webView The web view.
     * @return The rich push message, or null if the web view does not have an associated message.
     * @note This method should only be called from the main thread.
     */
    @MainThread
    @Nullable
    private Message getMessage(@NonNull WebView webView) {
        return MessageCenter.shared().getInbox().getMessageByUrl(webView.getUrl());
    }

}
