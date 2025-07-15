package com.urbanairship.javascript;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.AirshipExecutors;
import com.urbanairship.Cancelable;
import com.urbanairship.UALog;
import com.urbanairship.PendingResult;
import com.urbanairship.ResultCallback;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionCompletionCallback;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionRunRequestExtender;
import com.urbanairship.actions.ActionRunner;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.actions.DefaultActionRunner;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.UriUtils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Native bridge.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NativeBridge {

    /**
     * Airship's scheme. The web view client will override any
     * URLs that have this scheme by default.
     */
    @NonNull
    public static final String UA_ACTION_SCHEME = "uairship";

    /**
     * Run basic actions command.
     */
    @NonNull
    private static final String RUN_BASIC_ACTIONS_COMMAND = "run-basic-actions";

    /**
     * Run actions command.
     */
    @NonNull
    private static final String RUN_ACTIONS_COMMAND = "run-actions";

    /**
     * Run actions command with a callback.
     */
    @NonNull
    private static final String RUN_ACTIONS_COMMAND_CALLBACK = "run-action-cb";

    /**
     * Run actions command with a callback.
     */
    @NonNull
    private static final String SET_NAMED_USER_COMMAND = "named_user";

    /**
     * Key of the ActionValue list for setting a Named User.
     */
    @NonNull
    private static final String NAMED_USER_ARGUMENT_KEY = "id";

    /**
     * Close command to handle close method in the Javascript Interface.
     */
    @NonNull
    private static final String CLOSE_COMMAND = "close";

    /**
     * Multi command to handle running multiple commands.
     */
    @NonNull
    private static final String MULTI_COMMAND = "multi";

    private ActionCompletionCallback actionCompletionCallback;
    private final Executor executor;
    private final ActionRunner actionRunner;

    public interface CommandDelegate {
        void onClose();
        void onAirshipCommand(@NonNull String command, @NonNull Uri uri);
    }

    public NativeBridge() {
        this(DefaultActionRunner.INSTANCE, AirshipExecutors.newSerialExecutor());
    }

    public NativeBridge(@NonNull ActionRunner actionRunner) {
        this(actionRunner, AirshipExecutors.newSerialExecutor());
    }

    @VisibleForTesting
    NativeBridge(ActionRunner actionRunner, @NonNull Executor executor) {
        this.actionRunner = actionRunner;
        this.executor = executor;
    }

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param url The url being loaded.
     * @return <code>true</code> if the url was loaded, otherwise <code>false</code>.
     */
    @SuppressLint("LambdaLast")
    public boolean onHandleCommand(@Nullable String url,
                                   @NonNull JavaScriptExecutor javaScriptExecutor,
                                   @NonNull ActionRunRequestExtender actionRunRequestExtender,
                                   @NonNull CommandDelegate commandDelegate) {
        if (url == null) {
            return false;
        }

        Uri uri = Uri.parse(url);
        if (uri.getHost() == null || !UA_ACTION_SCHEME.equals(uri.getScheme())) {
            return false;
        }

        UALog.v("Intercepting: %s", url);

        switch (uri.getHost()) {
            case RUN_BASIC_ACTIONS_COMMAND:
                UALog.i("Running run basic actions command for URL: %s", url);
                runActions(actionRunRequestExtender, decodeActionArguments(uri, true));
                break;

            case RUN_ACTIONS_COMMAND:
                UALog.i("Running run actions command for URL: %s", url);
                runActions(actionRunRequestExtender, decodeActionArguments(uri, false));
                break;

            case RUN_ACTIONS_COMMAND_CALLBACK:
                UALog.i("Running run actions command with callback for URL: %s", url);

                List<String> paths = uri.getPathSegments();
                if (paths.size() == 3) {
                    UALog.i("Action: %s, Args: %s, Callback: %s", paths.get(0), paths.get(1), paths.get(2));
                    runAction(actionRunRequestExtender, javaScriptExecutor, paths.get(0), paths.get(1), paths.get(2));
                } else {
                    UALog.e("Unable to run action, invalid number of arguments.");
                }
                break;

            case SET_NAMED_USER_COMMAND:
                UALog.i("Running set Named User command for URL: %s", uri) ;
                Map<String, List<String>> args = UriUtils.getQueryParameters(uri);
                if (args.get(NAMED_USER_ARGUMENT_KEY) != null) {
                    String namedUser = args.get(NAMED_USER_ARGUMENT_KEY).get(0);
                    setNamedUserCommand(namedUser);
                } else if (args.get(NAMED_USER_ARGUMENT_KEY).get(0) == null) {
                    setNamedUserCommand(null);
                }
                break;

            case CLOSE_COMMAND:
                UALog.i("Running close command for URL: %s", url);
                commandDelegate.onClose();
                break;

            case MULTI_COMMAND:
                String[] urls = uri.getEncodedQuery().split("&");
                for (String parameterUrl : urls) {
                    String decodedUrl = Uri.decode(parameterUrl);
                    onHandleCommand(decodedUrl, javaScriptExecutor, actionRunRequestExtender, commandDelegate);
                }
                break;

            default:
                commandDelegate.onAirshipCommand(uri.getHost(), uri);
                break;
        }

        return true;
    }

    /**
     * Sets the action completion callback to be invoked whenever an {@link com.urbanairship.actions.Action}
     * is finished running from the web view.
     *
     * @param actionCompletionCallback The completion callback.
     */
    public void setActionCompletionCallback(@Nullable ActionCompletionCallback actionCompletionCallback) {
        this.actionCompletionCallback = actionCompletionCallback;
    }

    @NonNull
    public Cancelable loadJavaScriptEnvironment(@NonNull final Context context,
                                                @NonNull final JavaScriptEnvironment javaScriptEnvironment,
                                                @NonNull final JavaScriptExecutor javaScriptExecutor) {

        UALog.i("Loading Airship Javascript interface.");

        final PendingResult<String> pendingLoad = new PendingResult<>();
        pendingLoad.addResultCallback(Looper.myLooper(), javaScript -> {
            if (javaScript != null) {
                javaScriptExecutor.executeJavaScript(javaScript);
            }
        });

        executor.execute(() -> pendingLoad.setResult(javaScriptEnvironment.getJavaScript(context)));

        return pendingLoad;
    }

    /**
     * Runs a set of actions for the web view.
     *
     * @param actionRunRequestExtender The action request extender.
     * @param arguments Map of action to action arguments to run.
     */
    private void runActions(
            @NonNull ActionRunRequestExtender actionRunRequestExtender,
            @Nullable Map<String, List<ActionValue>> arguments
    ) {
        if (arguments == null) {
            return;
        }

        for (String actionName : arguments.keySet()) {
            List<ActionValue> args = arguments.get(actionName);
            if (args == null) {
                continue;
            }

            for (ActionValue arg : args) {
                actionRunner.run(
                        actionName,
                        arg,
                        Action.Situation.WEB_VIEW_INVOCATION,
                        actionRunRequestExtender,
                        actionCompletionCallback
                );
            }
        }
    }

    /**
     * Runs a single action by name, and performs callbacks into the JavaScript layer with results.
     *
     * @param actionRunRequestExtender The action request extender.
     * @param javaScriptExecutor The JavaScript executor.
     * @param name The name of the action to run.
     * @param value A JSON-encoded string representing the action value.
     * @param callbackKey The key for the callback function in JavaScript.
     */
    private void runAction(@NonNull ActionRunRequestExtender actionRunRequestExtender,
                           @NonNull final JavaScriptExecutor javaScriptExecutor,
                           @NonNull final String name,
                           @Nullable final String value,
                           @Nullable final String callbackKey) {
        // Parse the action value
        ActionValue actionValue;
        try {
            actionValue = new ActionValue(JsonValue.parseString(value));
        } catch (JsonException e) {
            UALog.e(e, "Unable to parse action argument value: %s", value);
            triggerCallback(javaScriptExecutor, "Unable to decode arguments payload", new ActionValue(), callbackKey);
            return;
        }



        actionRunner.run(
                name,
                actionValue,
                Action.Situation.WEB_VIEW_INVOCATION,
                actionRunRequestExtender,
                new ActionCompletionCallback() {
                    @Override
                    public void onFinish(@NonNull ActionArguments arguments, @NonNull ActionResult result) {

                        String errorMessage = null;

                        switch (result.getStatus()) {
                            case COMPLETED:
                                break;
                            case ACTION_NOT_FOUND:
                                errorMessage = String.format("Action %s not found", name);
                                break;
                            case REJECTED_ARGUMENTS:
                                errorMessage = String.format("Action %s rejected its arguments", name);
                                break;
                            case EXECUTION_ERROR:
                                if (result instanceof ActionResult.Error) {
                                    ActionResult.Error error = (ActionResult.Error)result;
                                    errorMessage = error.getException().getMessage();
                                } else {
                                    errorMessage = String.format("Action %s failed with unspecified error", name);
                                }
                        }

                        triggerCallback(javaScriptExecutor, errorMessage, result.getValue(), callbackKey);

                        synchronized (this) {
                            if (actionCompletionCallback != null) {
                                actionCompletionCallback.onFinish(arguments, result);
                            }
                        }
                    }
                }
        );

    }

    /**
     * Helper method that calls the action callback.
     *
     * @param javaScriptExecutor The JavaScript executor.
     * @param error The error message or null if no error.
     * @param resultValue The actions value of the result.
     * @param callbackKey The key for the callback function in JavaScript.
     */
    private void triggerCallback(@NonNull final JavaScriptExecutor javaScriptExecutor, @Nullable String error, @NonNull ActionValue resultValue, @Nullable String callbackKey) {
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

        javaScriptExecutor.executeJavaScript(finishAction);
    }

    /**
     * Decodes actions with basic URL or URL+json encoding
     *
     * @param uri The uri.
     * @param basicEncoding A boolean to select for basic encoding
     * @return A map of action values under action name strings or returns null if decoding error occurs.
     */
    private Map<String, List<ActionValue>> decodeActionArguments(@NonNull Uri uri, boolean basicEncoding) {
        Map<String, List<String>> options = UriUtils.getQueryParameters(uri);
        Map<String, List<ActionValue>> decodedActions = new HashMap<>();

        for (String actionName : options.keySet()) {
            List<ActionValue> decodedActionArguments = new ArrayList<>();

            if (options.get(actionName) == null) {
                UALog.w("No arguments to decode for actionName: %s", actionName);
                return null;
            }

            List<String> args = options.get(actionName);
            if (args == null) {
                continue;
            }
            for (String arg : args) {
                try {
                    JsonValue jsonValue = basicEncoding ? JsonValue.wrap(arg) : JsonValue.parseString(arg);
                    decodedActionArguments.add(new ActionValue(jsonValue));
                } catch (JsonException e) {
                    UALog.w(e, "Invalid json. Unable to create action argument "
                            + actionName + " with args: " + arg);
                    return null;
                }
            }

            decodedActions.put(actionName, decodedActionArguments);
        }

        if (decodedActions.isEmpty()) {
            UALog.w("Error no action names are present in the actions key set");
            return null;
        }

        return decodedActions;
    }

    /**
     * Helper method to set the named user through a Native Bridge command
     * @param namedUser
     */
    private void setNamedUserCommand(String namedUser) {
        if (namedUser != null) {
            namedUser = namedUser.trim();
        }
        if (UAStringUtil.isEmpty(namedUser)) {
            UAirship.shared().getContact().reset();
        } else {
            UAirship.shared().getContact().identify(namedUser);
        }
    }

}
