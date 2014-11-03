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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.webkit.HttpAuthHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunner;
import com.urbanairship.actions.Situation;
import com.urbanairship.js.NativeBridge;
import com.urbanairship.js.UAJavascriptInterface;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.JSONUtils;
import com.urbanairship.util.UriUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * A web view client that intercepts Urban Airship URLs and enables triggering
 * actions from javascript.
 * </p>
 * <p/>
 * <p>
 * The UAWebViewClient will intercept links with the 'uairship' scheme and with
 * the commands (supplied as the host) 'run-actions' or 'run-basic-actions'.
 * </p>
 * <p/>
 * <p>
 * The run-actions command runs a set of actions listed in the URL's query
 * options, by providing key=value pairs, where each pair's key is the name of
 * an action and the value is a JSON encoded string representing the value of
 * the action's {@link com.urbanairship.actions.ActionArguments}. The JSON
 * encoded string is decoded and converted to a List<Object> if the argument is
 * a JSONArray or a Map<String, Object> if the argument is a JSONObject.
 * </p>
 * <p/>
 * <p>
 * Example: uairship://run-actions?&add_tags=%5B%22one%22%2C%22two%22%5D
 * will run the "add_tags" with value "["one", "two"]".
 * </p>
 * <p/>
 * <p>
 * The run-basic-actions command is similar to run-actions, but the argument value
 * is treated as a string literal.
 * </p>
 * <p/>
 * <p>
 * Example: uairship://run-basic-actions?add_tags=one&remove_tags=two will run
 * the "add_tags" with the value "one", and perform the "remove_tags" action with
 * value "two".
 * </p>
 * <p/>
 * <p>
 * When extending this class, any overridden methods should call through to the
 * super class' implementations.
 * </p>
 */
public class UAWebViewClient extends WebViewClient {

    /**
     * Urban Airship's scheme. The web view client will override any
     * URLs that have this scheme by default.
     */
    public static final String UA_ACTION_SCHEME = "uairship";

    /**
     * Run basic actions command.
     */
    public static final String RUN_BASIC_ACTIONS_COMMAND = "run-basic-actions";

    /**
     * Run actions command.
     */
    public static final String RUN_ACTIONS_COMMAND = "run-actions";

    /**
     * Run actions command with a callback. Maps to
     * {@link com.urbanairship.js.UAJavascriptInterface#runActionCallback(String, Object, String)}.
     */
    private static final String RUN_ACTIONS_COMMAND_CALLBACK = "android-run-action-cb";

    /**
     * Close command to handle close method in the Javascript Interface.
     */
    private static final String CLOSE_COMMAND = "close";

    private ActionRunner actionRunner;

    private Map<String, Credentials> authRequestCredentials = new HashMap<String, Credentials>();

    public UAWebViewClient() {
        this(ActionRunner.shared());
    }

    UAWebViewClient(ActionRunner runner) {
        this.actionRunner = runner;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return interceptUrl(view, url);
    }

    @Override
    public void onLoadResource(WebView view, String url) {

        /*
         * Sometimes shouldOverrideUrlLoading is not called when the uairship library is ready for whatever reasons,
         * but once shouldOverrideUrlLoading is called and returns true it will prevent onLoadResource from
         * being called with the url.
         */

        interceptUrl(view, url);
    }

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param view The web view.
     * @param url The url being loaded.
     * @return <code>true</code> if the url was loaded, otherwise <code>false</code>.
     */
    private boolean interceptUrl(WebView view, String url) {
        Uri uri = Uri.parse(url);

        if (!uri.getScheme().equals(UA_ACTION_SCHEME) || view == null || !isWhiteListed(view.getUrl())) {
            return false;
        }

        RichPushMessage message = null;
        if (view instanceof RichPushMessageWebView) {
            message = ((RichPushMessageWebView) view).getCurrentMessage();
        }

        Logger.verbose("Intercepting: " + url);
        if (RUN_BASIC_ACTIONS_COMMAND.equals(uri.getHost())) {
            Logger.info("Running run basic actions command for URL: " + url);
            Map<String, List<String>> options = UriUtils.getQueryParameters(uri);
            runBasicActions(options, message);
        } else if (RUN_ACTIONS_COMMAND.equals(uri.getHost())) {
            Logger.info("Running run actions command for URL: " + url);
            Map<String, List<String>> options = UriUtils.getQueryParameters(uri);
            runActions(options, message);
        } else if (RUN_ACTIONS_COMMAND_CALLBACK.equals(uri.getHost())) {
            Logger.info("Running run actions command with callback for URL: " + url);
            UAJavascriptInterface jsInterface = new UAJavascriptInterface(view, message);
            List<String> paths = uri.getPathSegments();
            if (paths.size() == 3) {
                Logger.info("Action: " + paths.get(0) + ", Args: " + paths.get(1) + ", Callback: " + paths.get(2));
                jsInterface.actionCall(paths.get(0), paths.get(1), paths.get(2));
            } else {
                Logger.error("Unable to run action, invalid number of arguments.");
            }
        } else if (CLOSE_COMMAND.equals(uri.getHost())) {
            Logger.info("Running close command for URL: " + url);
            UAJavascriptInterface jsInterface = new UAJavascriptInterface(view, message);
            jsInterface.close();
        } else {
            Logger.warn("Unrecognized command: " + uri.getHost()
                    + " for URL: " + url);

            return false;
        }

        return true;
    }

    /**
     * Performs actions from the run actions command.
     *
     * @param actions Map of action name to action arguments.
     * @param message The rich push message associated with the web view.
     */
    private void runActions(Map<String, List<String>> actions, RichPushMessage message) {
        Map<String, List<ActionArguments>> decodedActionArguments = decodeActionArguments(actions, message, false);

        if (decodedActionArguments != null) {
            for (String actionName : decodedActionArguments.keySet()) {
                for (ActionArguments decodedActionArgument : decodedActionArguments.get(actionName)) {
                    actionRunner.runAction(actionName, decodedActionArgument);

                }
            }
        }

    }

    /**
     * Performs actions from the run basic actions command.
     *
     * @param actions Map of action name to action arguments
     * @param message The rich push message associated with the web view.
     */
    private void runBasicActions(Map<String, List<String>> actions, RichPushMessage message) {
        Map<String, List<ActionArguments>> decodedActionArguments = decodeActionArguments(actions, message, true);

        if (decodedActionArguments != null) {
            for (String actionName : decodedActionArguments.keySet()) {
                for (ActionArguments decodedActionArgument : decodedActionArguments.get(actionName)) {
                    actionRunner.runAction(actionName, decodedActionArgument);

                }
            }
        }
    }

    /**
     * Decodes actions with basic URL or URL+json encoding
     *
     * @param actions The map of actions to decode
     * @param message The rich push message associated with the web view.
     * @param basicEncoding A boolean to select for basic encoding
     * @return A map of action arguments under action name strings or returns null if decoding error occurs.
     */
    private Map<String, List<ActionArguments>> decodeActionArguments (Map<String, List<String>> actions, RichPushMessage message, boolean basicEncoding) {
        HashMap<String, List<ActionArguments>> decodedActions = new HashMap();
        List<ActionArguments> decodedActionArguments = new ArrayList<ActionArguments>();

        for (String actionName : actions.keySet()) {
            List<String> args = actions.get(actionName);

            if (args == null) {
                Logger.warn("No arguments to decode for actionName: " + actionName);
                return null;
            }

            for (String arg : args) {
                try {
                    ActionArguments arguments;
                    if (basicEncoding) {
                        arguments = new ActionArguments.Builder()
                                .setSituation(Situation.WEB_VIEW_INVOCATION)
                                .setValue(arg)
                                .addMetadata(ActionArguments.RICH_PUSH_METADATA, message)
                                .create();
                    } else {
                        arguments = new ActionArguments.Builder()
                                .setSituation(Situation.WEB_VIEW_INVOCATION)
                                .setValue(getArgumentFromJSONString(arg))
                                .addMetadata(ActionArguments.RICH_PUSH_METADATA, message)
                                .create();
                    }
                    decodedActionArguments.add(arguments);
                } catch (JSONException e) {
                    Logger.warn("Invalid json. Unable to create action argument "
                            + actionName + " with args: " + arg, e);
                    return null;
                }
            }

            decodedActions.put(actionName, new ArrayList<ActionArguments>(decodedActionArguments));
            decodedActionArguments.clear();
        }

        if (decodedActions.isEmpty()) {
            Logger.warn("Error no action names are present in the actions key set");
            return null;
        }

        return decodedActions;
    }

    /**
     * Converts a json string to either a map or list that represents
     * the json.
     *
     * @param jsonString json string to convert.
     * @return A map or list from the json string.
     * @throws JSONException If the jsonString is not valid json.
     */
    private Object getArgumentFromJSONString(String jsonString) throws JSONException {
        if (jsonString == null) {
            return null;
        }

        try {
            return JSONUtils.convertToList(new JSONArray(jsonString));
        } catch (JSONException e) {
            return JSONUtils.convertToMap(new JSONObject(jsonString));
        }
    }

    @Override
    @SuppressLint("NewAPI")
    public void onPageFinished(WebView view, String url) {
        if (view == null || !isWhiteListed(url)) {
            return;
        }

        if (Build.VERSION.SDK_INT < 17) {
            Logger.info("Loading UrbanAirship Javascript interface.");

            RichPushMessage message = null;
            if (view instanceof RichPushMessageWebView) {
                message = ((RichPushMessageWebView) view).getCurrentMessage();
            }

            UAJavascriptInterface jsInterface = new UAJavascriptInterface(view, message);
            String jsWrapper = createJavascriptInterfaceWrapper(jsInterface);
            view.loadUrl("javascript:" + jsWrapper);
        }

        // Load the native bridge
        Logger.info("Loading UrbanAirship native Javascript bridge.");
        String nativeBridge = NativeBridge.getJavaScriptSource();
        if (Build.VERSION.SDK_INT >= 19) {
            view.evaluateJavascript(nativeBridge, null);
        } else {
            view.loadUrl("javascript:" + nativeBridge);
        }
    }

    @Override
    @SuppressLint("NewAPI")
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        if (view == null) {
            return;
        }

        // Always remove the old interface if supported
        if (Build.VERSION.SDK_INT >= 17) {
            view.removeJavascriptInterface(UAJavascriptInterface.JAVASCRIPT_IDENTIFIER);
        }

        if (!isWhiteListed(url)) {
            Logger.warn( url + " is not a white listed URL. Urban Airship Javascript interface will not be accessible.");
            return;
        }



        if (Build.VERSION.SDK_INT >= 17) {
            if (view instanceof RichPushMessageWebView) {
                view.addJavascriptInterface(new UAJavascriptInterface(view, ((RichPushMessageWebView) view).getCurrentMessage()), UAJavascriptInterface.JAVASCRIPT_IDENTIFIER);
            } else {
                view.addJavascriptInterface(new UAJavascriptInterface(view), UAJavascriptInterface.JAVASCRIPT_IDENTIFIER);
            }
        }
    }

    /**
     * Checks if the URL is white listed.
     *
     * @param url The URL being loaded.
     * @return <code>true</code> if the URL is white listed, otherwise <code>false</code>.
     */
    private boolean isWhiteListed(String url) {
        if (!UAirship.shared().getWhitelist().isWhitelisted(url)) {
            return false;
        }

        return true;
    }

    @Override
    public void onReceivedHttpAuthRequest(WebView view, HttpAuthHandler handler, String host,
                                          String realm) {
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
    void addAuthRequestCredentials(String expectedAuthHost, String username, String password) {
        authRequestCredentials.put(expectedAuthHost, new Credentials(username, password));
    }

    /**
     * Removes auth request credentials for a host.
     *
     * @param expectedAuthHost The expected host.
     */
    void removeAuthRequestCredentials(String expectedAuthHost) {
        authRequestCredentials.remove(expectedAuthHost);
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


    String createJavascriptInterfaceWrapper(UAJavascriptInterface jsInterface) {
        String stringMethod = "_UAirship.%s = function(){return '%s';};";
        String literalMethod = "_UAirship.%s = function(){return %s;};";

        StringBuilder sb = new StringBuilder().append("var _UAirship = {};");

        // Getters
        sb.append(String.format(stringMethod, "getDeviceModel", jsInterface.getDeviceModel()))
          .append(String.format(stringMethod, "getMessageId", jsInterface.getMessageId()))
          .append(String.format(stringMethod, "getMessageTitle", jsInterface.getMessageTitle()))
          .append(String.format(stringMethod, "getMessageSentDate", jsInterface.getMessageSentDate()))
          .append(String.format(literalMethod, "getMessageSentDateMS", jsInterface.getMessageSentDateMS()))
          .append(String.format(stringMethod, "getUserId", jsInterface.getUserId()));

        // Invoke helper method
        sb.append("_UAirship.invoke = function(url){")
          .append("var f = document.createElement('iframe');")
          .append("f.style.display = 'none';")
          .append("f.src = url;")
          .append("document.body.appendChild(f);")
          .append("f.parentNode.removeChild(f);")
          .append("};");

        // Close
        sb.append("_UAirship.close=function(){_UAirship.invoke('uairship://close');};");

        // ActionCall
        sb.append("_UAirship.actionCall=function(name, args, callback){")
          .append("var url = 'uairship://android-run-action-cb/' + name + '/' + encodeURIComponent(args) +'/' + callback;")
          .append("_UAirship.invoke(url);")
          .append("};");

        // NavigateTo
        sb.append("_UAirship.navigateTo=function(activity){_UAirship.invoke('uairship://navigate-to/' + activity);};");

        // MarkMessageRead
        sb.append("_UAirship.markMessageRead=function(){")
          .append("_UAirship.invoke('uairship://mark-message-read');")
          .append(String.format(literalMethod, "isMessageRead", true))
          .append("};");

        // MarkMessageUnread
        sb.append("_UAirship.markMessageUnread=function(){")
          .append("_UAirship.invoke('uairship://mark-message-unread');")
          .append(String.format(literalMethod, "isMessageRead", false))
          .append("};");

        return sb.toString();
    }

}
