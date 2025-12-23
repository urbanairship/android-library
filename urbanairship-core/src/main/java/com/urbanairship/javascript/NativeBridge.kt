package com.urbanairship.javascript

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipExecutors.newSerialExecutor
import com.urbanairship.Cancelable
import com.urbanairship.PendingResult
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionCompletionCallback
import com.urbanairship.actions.ActionResult
import com.urbanairship.actions.ActionRunRequestExtender
import com.urbanairship.actions.ActionRunner
import com.urbanairship.actions.ActionValue
import com.urbanairship.actions.DefaultActionRunner
import com.urbanairship.contacts.Contact
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.util.UriUtils
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject

/**
 * Native bridge.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NativeBridge @VisibleForTesting public constructor(
    private val actionRunner: ActionRunner = DefaultActionRunner,
    private val executor: Executor = newSerialExecutor(),
    private val contactProvider: () -> Contact = { Airship.contact }
) {

    private val callback = MutableStateFlow<ActionCompletionCallback?>(null)

    public interface CommandDelegate {
        public fun onClose()
        public fun onAirshipCommand(command: String, uri: Uri)
    }

    /**
     * Intercepts a url for our JS bridge.
     *
     * @param url The url being loaded.
     * @return `true` if the url was loaded, otherwise `false`.
     */
    @SuppressLint("LambdaLast")
    public fun onHandleCommand(
        url: String?,
        javaScriptExecutor: JavaScriptExecutor,
        actionRunRequestExtender: ActionRunRequestExtender,
        commandDelegate: CommandDelegate
    ): Boolean {
        if (url == null) {
            return false
        }

        val uri = Uri.parse(url)
        val host = uri.host
        if (host == null || UA_ACTION_SCHEME != uri.scheme) {
            return false
        }

        UALog.v("Intercepting: $url")

        when (uri.host) {
            RUN_BASIC_ACTIONS_COMMAND -> {
                UALog.i("Running run basic actions command for URL: $url")
                runActions(actionRunRequestExtender, decodeActionArguments(uri, true))
            }

            RUN_ACTIONS_COMMAND -> {
                UALog.i("Running run actions command for URL: $url")
                runActions(actionRunRequestExtender, decodeActionArguments(uri, false))
            }

            RUN_ACTIONS_COMMAND_CALLBACK -> {
                UALog.i("Running run actions command with callback for URL: $url")

                val paths = uri.pathSegments
                if (paths.size == 3) {
                    UALog.i("Action: ${paths[0]}, Args: ${paths[1]}, Callback: ${paths[2]}")
                    runAction(
                        actionRunRequestExtender, javaScriptExecutor, paths[0], paths[1], paths[2]
                    )
                } else {
                    UALog.e("Unable to run action, invalid number of arguments.")
                }
            }

            SET_NAMED_USER_COMMAND -> {
                UALog.i("Running set Named User command for URL: $uri")
                val args = UriUtils.getQueryParameters(uri)
                val namedUser = args[NAMED_USER_ARGUMENT_KEY]?.firstOrNull()
                setNamedUserCommand(namedUser)
            }

            CLOSE_COMMAND, DISMISS_COMMAND -> {
                UALog.i("Running close command for URL: $url")
                commandDelegate.onClose()
            }

            MULTI_COMMAND -> {
                uri.encodedQuery
                    ?.split("&")
                    ?.dropLastWhile { it.isEmpty() }
                    ?.forEach { command ->
                        val decodedUrl = Uri.decode(command)
                        onHandleCommand(
                            url = decodedUrl,
                            javaScriptExecutor = javaScriptExecutor,
                            actionRunRequestExtender = actionRunRequestExtender,
                            commandDelegate = commandDelegate
                        )
                    }
            }

            else -> commandDelegate.onAirshipCommand(host, uri)
        }

        return true
    }

    /**
     * Sets the action completion callback to be invoked whenever an [com.urbanairship.actions.Action]
     * is finished running from the web view.
     *
     * @param actionCompletionCallback The completion callback.
     */
    public fun setActionCompletionCallback(actionCompletionCallback: ActionCompletionCallback?) {
        callback.update { actionCompletionCallback }
    }

    public fun loadJavaScriptEnvironment(
        context: Context,
        javaScriptEnvironment: JavaScriptEnvironment,
        javaScriptExecutor: JavaScriptExecutor
    ): Cancelable {
        UALog.i("Loading Airship Javascript interface.")

        val pendingLoad = PendingResult<String>()
        pendingLoad.addResultCallback(Looper.myLooper()) { javaScript: String? ->
            javaScript?.let(javaScriptExecutor::executeJavaScript)
        }

        executor.execute { pendingLoad.setResult(javaScriptEnvironment.getJavaScript(context)) }

        return pendingLoad
    }

    /**
     * Runs a set of actions for the web view.
     *
     * @param actionRunRequestExtender The action request extender.
     * @param arguments Map of action to action arguments to run.
     */
    private fun runActions(
        actionRunRequestExtender: ActionRunRequestExtender,
        arguments: Map<String, List<ActionValue>>?
    ) {
        if (arguments == null) {
            return
        }

        arguments.forEach { (actionName, args) ->
            args.forEach { actionArgs ->
                actionRunner.run(
                    name = actionName,
                    value = actionArgs,
                    situation = Action.Situation.WEB_VIEW_INVOCATION,
                    extender = actionRunRequestExtender,
                    callback = callback.value
                )
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
    private fun runAction(
        actionRunRequestExtender: ActionRunRequestExtender,
        javaScriptExecutor: JavaScriptExecutor,
        name: String,
        value: String?,
        callbackKey: String?
    ) {
        // Parse the action value
        val actionValue: ActionValue
        try {
            actionValue = ActionValue(JsonValue.parseString(value))
        } catch (e: JsonException) {
            UALog.e(e, "Unable to parse action argument value: $value")
            triggerCallback(
                javaScriptExecutor, "Unable to decode arguments payload", ActionValue(), callbackKey
            )
            return
        }

        actionRunner.run(
            name = name,
            value = actionValue,
            situation = Action.Situation.WEB_VIEW_INVOCATION,
            extender = actionRunRequestExtender,
            callback = { arguments, result ->
                val errorMessage = when (result.status) {
                    ActionResult.Status.ACTION_NOT_FOUND -> "Action $name not found"
                    ActionResult.Status.REJECTED_ARGUMENTS -> "Action $name rejected its arguments"
                    ActionResult.Status.EXECUTION_ERROR -> {
                        if (result is ActionResult.Error) {
                            result.exception.message
                        } else {
                            "Action $name failed with unspecified error"
                        }
                    }
                    ActionResult.Status.COMPLETED -> null
                }

                triggerCallback(
                    javaScriptExecutor = javaScriptExecutor,
                    error = errorMessage,
                    resultValue = result.value,
                    callbackKey = callbackKey)

                callback.value?.onFinish(arguments, result)
            })
    }

    /**
     * Helper method that calls the action callback.
     *
     * @param javaScriptExecutor The JavaScript executor.
     * @param error The error message or null if no error.
     * @param resultValue The actions value of the result.
     * @param callbackKey The key for the callback function in JavaScript.
     */
    private fun triggerCallback(
        javaScriptExecutor: JavaScriptExecutor,
        error: String?,
        resultValue: ActionValue,
        callbackKey: String?
    ) {
        // Create the callback string
        val callbackString = String.format("'%s'", callbackKey)

        // Create the error string
        val errorString = if (error != null) {
            String.format(
                Locale.US, "new Error(%s)", JSONObject.quote(error)
            )
        } else "null"

        // Create the result value
        val resultValueString = resultValue.toString()

        // Create the javascript call for Airship.finishAction(error, value, callback)
        val finishAction = String.format(
            Locale.US,
            "UAirship.finishAction(%s, %s, %s);",
            errorString,
            resultValueString,
            callbackString
        )

        javaScriptExecutor.executeJavaScript(finishAction)
    }

    /**
     * Decodes actions with basic URL or URL+json encoding
     *
     * @param uri The uri.
     * @param basicEncoding A boolean to select for basic encoding
     * @return A map of action values under action name strings or returns null if decoding error occurs.
     */
    private fun decodeActionArguments(
        uri: Uri,
        basicEncoding: Boolean
    ): Map<String, List<ActionValue>>? {
        val options = UriUtils.getQueryParameters(uri)
        val decodedActions = options
            .mapValues { (name, args) ->
                args.map {
                    try {
                        val json = if (basicEncoding) JsonValue.wrap(it) else JsonValue.parseString(it)
                        ActionValue(json)
                    } catch (e: JsonException) {
                        UALog.w(e, "Invalid json. Unable to create action argument $name with args: $it")
                        return null
                    }
                }
            }

        if (decodedActions.isEmpty()) {
            UALog.w("Error no action names are present in the actions key set")
            return null
        }

        return decodedActions
    }

    /**
     * Helper method to set the named user through a Native Bridge command
     * @param namedUser
     */
    private fun setNamedUserCommand(namedUser: String?) {
        val trimmedName = namedUser?.trim { it <= ' ' }
        if (trimmedName.isNullOrEmpty()) {
            contactProvider().reset()
        } else {
            contactProvider().identify(trimmedName)
        }
    }

    public companion object {

        /**
         * Airship's scheme. The web view client will override any
         * URLs that have this scheme by default.
         */
        public const val UA_ACTION_SCHEME: String = "uairship"

        /**
         * Run basic actions command.
         */
        private const val RUN_BASIC_ACTIONS_COMMAND = "run-basic-actions"

        /**
         * Run actions command.
         */
        private const val RUN_ACTIONS_COMMAND = "run-actions"

        /**
         * Run actions command with a callback.
         */
        private const val RUN_ACTIONS_COMMAND_CALLBACK = "run-action-cb"

        /**
         * Run actions command with a callback.
         */
        private const val SET_NAMED_USER_COMMAND = "named_user"

        /**
         * Key of the ActionValue list for setting a Named User.
         */
        private const val NAMED_USER_ARGUMENT_KEY = "id"

        /**
         * Close command to handle close method in the Javascript Interface.
         */
        private const val CLOSE_COMMAND = "close"
        private const val DISMISS_COMMAND = "dismiss"

        /**
         * Multi command to handle running multiple commands.
         */
        private const val MULTI_COMMAND = "multi"
    }
}
