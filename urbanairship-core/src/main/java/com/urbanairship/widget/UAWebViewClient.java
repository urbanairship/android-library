/* Copyright Airship and Contributors */

package com.urbanairship.widget;

import com.urbanairship.messagecenter.MessageWebViewClient;
import com.urbanairship.webkit.AirshipWebViewClient;

import androidx.annotation.NonNull;

/**
 * <p>
 * A web view client that intercepts Airship URLs and enables triggering
 * actions from javascript.
 * <p>
 * <p>
 * <p>
 * The UAWebViewClient will intercept links with the 'uairship' scheme and with
 * the commands (supplied as the host) 'run-actions' or 'run-basic-actions'.
 * <p>
 * <p>
 * <p>
 * The run-actions command runs a set of actions listed in the URL's query
 * options, by providing key=value pairs, where each pair's key is the name of
 * an action and the value is a JSON encoded string representing the value of
 * the action's {@link com.urbanairship.actions.ActionArguments}. The JSON
 * encoded string is decoded and converted to a List<Object> if the argument is
 * a JSONArray or a Map<String, Object> if the argument is a JSONObject.
 * <p>
 * <p>
 * <p>
 * Example: uairship://run-actions?&add_tags_action=%5B%22one%22%2C%22two%22%5D
 * will run the "add_tags_action" with value "["one", "two"]".
 * <p>
 * <p>
 * <p>
 * The run-basic-actions command is similar to run-actions, but the argument value
 * is treated as a string literal.
 * <p>
 * <p>
 * <p>
 * Example: uairship://run-basic-actions?add_tags_action=one&remove_tags_action=two will run
 * the "add_tags_action" with the value "one", and perform the "remove_tags_action" action with
 * value "two".
 * <p>
 * <p>
 * <p>
 * When extending this class, any overridden methods should call through to the
 * super class' implementations.
 * <p>
 *
 * @deprecated Use {@link AirshipWebViewClient} or {@link MessageWebViewClient} instead.
 */
@Deprecated
public class UAWebViewClient extends MessageWebViewClient {

    /**
     * Airship's scheme. The web view client will override any
     * URLs that have this scheme by default.
     */
    @Deprecated
    @NonNull
    public static final String UA_ACTION_SCHEME = "uairship";

    /**
     * Run basic actions command.
     */
    @Deprecated
    @NonNull
    public static final String RUN_BASIC_ACTIONS_COMMAND = "run-basic-actions";

    /**
     * Run actions command.
     */
    @Deprecated
    @NonNull
    public static final String RUN_ACTIONS_COMMAND = "run-actions";

    /**
     * Run actions command with a callback.
     */
    @Deprecated
    private static final String RUN_ACTIONS_COMMAND_CALLBACK = "run-action-cb";

    /**
     * Close command to handle close method in the Javascript Interface.
     */
    @Deprecated
    @NonNull
    public static final String CLOSE_COMMAND = "close";

}
