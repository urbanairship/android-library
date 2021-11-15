package com.urbanairship.chat

import com.urbanairship.Logger
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.util.AirshipComponentUtils
import java.util.concurrent.Callable

/**
 * Sends a chat message.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument values: JsonObject with the message string to send under `message` key and optional ChatRouting object under `chat_routing` key.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: send_chat_action
 */
class SendChatAction(private val chatCallable: Callable<Chat> = AirshipComponentUtils.callableForComponent(Chat::class.java)) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        if (arguments.value.map != null) {
            val message = arguments.value.map?.opt("message")?.string
            val routing = ChatRouting.fromJsonMap(arguments.value.map?.opt("chat_routing")?.optMap())
            require(message != null || routing != null) {
                Logger.error("Both message and routing should not be null.")
                return false
            }
        } else {
            return false
        }

        return when (arguments.situation) {
            SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION, SITUATION_MANUAL_INVOCATION, SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_AUTOMATION -> true
            SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_PUSH_RECEIVED -> false
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val message = arguments.value.map?.opt("message")?.string
        val routing = ChatRouting.fromJsonMap(arguments.value.map?.opt("chat_routing")?.optMap())

        if (!routing.agent.isNullOrEmpty()) {
            chatCallable.call().conversation.routing = routing
        }

        if (!message.isNullOrEmpty()) {
            chatCallable.call().conversation.sendMessage(message)
        }

        return ActionResult.newEmptyResult()
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }
}
