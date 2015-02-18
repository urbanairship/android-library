/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.js;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequestFactory;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.Situation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.richpush.RichPushMessage;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * The Urban Airship Javascript interface.
 */
public class UAJavascriptInterface {

    /**
     * The Urban Airship javascript identifier that is injected into the web views. After the final
     * native bridge is injected, "UAirship" identifier will wrap "_UAirship".
     */
    public static final String JAVASCRIPT_IDENTIFIER = "_UAirship";

    private static SimpleDateFormat dateFormatter;
    private final RichPushMessage message;
    private final ActionRunRequestFactory actionRequestFactory;
    private final WebView webView;
    private ActionCompletionCallback actionCompletionCallback;

    /**
     * Default constructor.
     * <p/>
     * Subclasses should always call super().
     *
     * @param webView The WebView associated with this interface.
     */
    public UAJavascriptInterface(WebView webView) {
        this(webView, null);
    }

    /**
     * Creates a Javascript Interface with a RichPushMessage.
     *
     * @param webView The WebView associated with this interface.
     * @param message The rich push message.
     */
    public UAJavascriptInterface(WebView webView, RichPushMessage message) {
        this(webView, message, new ActionRunRequestFactory());
    }

    /**
     * Constructs the Javascript Interface with the specified RichPushMessage and ActionRunRequestFactory.
     *
     * @param webView The WebView associated with this interface.
     * @param message The rich push message.
     * @param actionRequestFactory The action request factory.
     */
    UAJavascriptInterface(WebView webView, RichPushMessage message, ActionRunRequestFactory actionRequestFactory) {
        this.webView = webView;
        this.message = message;
        this.actionRequestFactory = actionRequestFactory;
    }


    /**
     * Sets the action completion callback.
     *
     * @param actionCompletionCallback The action completion callback.
     */
    public void setActionCompletionCallback(ActionCompletionCallback actionCompletionCallback) {
        synchronized (this) {
            this.actionCompletionCallback = actionCompletionCallback;
        }
    }

    /**
     * Get the device's human readable model name. (Galaxy Nexus, Nexus 7, etc.).
     *
     * @return The device's model name.
     */
    @JavascriptInterface
    public String getDeviceModel() {
        return Build.MODEL;
    }

    /**
     * Get the current {@link RichPushMessage}'s ID.
     *
     * @return The {@link RichPushMessage}'s ID as a String or null if the message
     * is not available.
     */
    @JavascriptInterface
    public String getMessageId() {
        return message != null ? message.getMessageId() : null;
    }

    /**
     * Get the current {@link RichPushMessage}'s title.
     *
     * @return The message's title or null if the message is not available.
     */
    @JavascriptInterface
    public String getMessageTitle() {
        return message != null ? message.getTitle() : null;
    }

    /**
     * Get the current {@link RichPushMessage}'s sent date (UTC).
     *
     * @return The {@link RichPushMessage}'s sent date or null if the message
     * is not available.
     */
    @JavascriptInterface
    public String getMessageSentDate() {
        if (message == null) {
            return null;
        }

        if (dateFormatter == null) {
            dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US);
            dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        return dateFormatter.format(this.message.getSentDate());
    }

    /**
     * Get the current {@link RichPushMessage}'s sent date (unix epoch time in milliseconds).
     *
     * @return The {@link RichPushMessage}'s sent date (unix epoch time in milliseconds), or -1 if
     * the message is unavailable.
     */
    @JavascriptInterface
    public long getMessageSentDateMS() {
        return message != null ? message.getSentDateMS() : -1;
    }

    /**
     * Get the Rich Push user's ID.
     *
     * @return The Rich Push user's ID as a String.
     */
    @JavascriptInterface
    public String getUserId() {
        return UAirship.shared().getRichPushManager().getRichPushUser().getId();
    }

    /**
     * Attempts to close the current web view by simulating a back key press.
     * <p/>
     * To override the default behavior, intercept the KeyEvent.KEYCODE_BACK by
     * overriding the web view's onKeyDown method.
     */
    @JavascriptInterface
    public void close() {
        webView.post(new Runnable() {
            @Override
            public void run() {
                webView.getRootView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                webView.getRootView().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
            }
        });
    }

    /**
     * Runs an action by name, and performs callbacks into the JavaScript layer with results.
     *
     * @param name The name of the action to run.
     * @param value A JSON-encoded string representing the action value.
     * @param callbackKey The key for the callback function in JavaScript.
     */
    /*
     * Once the action is finished (or has resulted in an unsuccessful status) This method will call
     * back into the the finishAction function in the JavaScript layer.
     */
    @JavascriptInterface
    public void actionCall(final String name, final String value, final String callbackKey) {
        // Parse the action value
        ActionValue actionValue;
        try {
            actionValue = new ActionValue(JsonValue.parseString(value));
        } catch (JsonException e) {
            Logger.warn("Unable to parse action argument value: " + value, e);
            runActionCallback("Unable to decode arguments payload", new ActionValue(), callbackKey);
            return;
        }

        // Create metadata
        Bundle metadata = new Bundle();
        if (message != null) {
            metadata.putString(ActionArguments.RICH_PUSH_ID_METADATA, message.getMessageId());
        }

        // Run the action
        actionRequestFactory.createActionRequest(name)
                            .setMetadata(metadata)
                            .setValue(actionValue)
                            .setSituation(Situation.WEB_VIEW_INVOCATION)
                            .run(new ActionCompletionCallback() {
                                @Override
                                public void onFinish(ActionArguments arguments, ActionResult result) {
                                    String errorMessage = createErrorMessageFromResult(name, result);
                                    runActionCallback(errorMessage, result.getValue(), callbackKey);

                                    synchronized (this) {
                                        if (actionCompletionCallback != null) {
                                            actionCompletionCallback.onFinish(arguments, result);
                                        }
                                    }
                                }
                            });
    }

    /**
     * Helper method that calls the action callback.
     *
     * @param error The error message or null if no error.
     * @param resultValue The actions value of the result.
     * @param callbackKey The key for the callback function in JavaScript.
     */
    @SuppressLint("NewAPI")
    private void runActionCallback(String error, ActionValue resultValue, String callbackKey) {
        // Create the callback string
        String callbackString = String.format("'%s'", callbackKey);

        // Create the error string
        String errorString;
        if (error == null) {
            errorString = "null";
        } else {
            errorString = String.format(Locale.US, "new Error(%s)", JSONObject.quote(error));
        }

        // Create the result value
        String resultValueString = resultValue.toString();

        // Create the javascript call for UAirship.finishAction(error, value, callback)
        final String finishAction = String.format(Locale.US, "UAirship.finishAction(%s, %s, %s);",
                errorString, resultValueString, callbackString);

        // Call on main thread
        webView.post(new Runnable() {
            @Override
            public void run() {
                if (Build.VERSION.SDK_INT >= 19) {
                    webView.evaluateJavascript(finishAction, null);
                } else {
                    webView.loadUrl("javascript:" + finishAction);
                }
            }
        });
    }

    /**
     * Creates an error message from an action result.
     *
     * @param actionName The name of the action.
     * @param result The action result.
     * @return An error message if the result has an error, else null.
     */
    private String createErrorMessageFromResult(String actionName, ActionResult result) {
        switch (result.getStatus()) {
            case ACTION_NOT_FOUND:
                return String.format("Action %s not found", actionName);
            case REJECTED_ARGUMENTS:
                return String.format("Action %s rejected its arguments", actionName);
            case EXECUTION_ERROR:
                if (result.getException() != null) {
                    return result.getException().getMessage();
                }
                return String.format("Action %s failed with unspecified error", actionName);
            default:
                return null;
        }
    }
}
