package com.urbanairship.chat

import com.urbanairship.Logger
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.json.JsonException
import com.urbanairship.util.AirshipComponentUtils
import java.util.concurrent.Callable

/**
 * Opens the chat.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument values: {@code null} or a JsonObject with the prefilled message under `chat_input` key.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: open_chat_action
 */
class OpenChatAction(private val chatCallable: Callable<Chat> = AirshipComponentUtils.callableForComponent(Chat::class.java)) : Action() {

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION, SITUATION_MANUAL_INVOCATION, SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_AUTOMATION -> true
            SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_PUSH_RECEIVED -> false
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val message = arguments.value.map?.opt("chat_input")?.string
        val routing = ChatRouting.fromJsonMap(arguments.value.map?.opt("chat_routing")?.optMap())
        val incoming = arguments.value.map?.opt("prepopulated_messages")?.string

        if (!routing.agent.isNullOrEmpty()) {
            chatCallable.call().conversation.routing = routing
        }

        if (!incoming.isNullOrEmpty()) {
            try {
                val messages = ChatIncomingMessage.getListFromJSONArrayString(incoming)
                chatCallable.call().conversation.addIncoming(messages)
            } catch (e: JsonException) {
                Logger.error("Failed to parse outgoing messages", e)
            }
        }

        chatCallable.call().openChat(message)
        return ActionResult.newEmptyResult()
    }

    override fun shouldRunOnMainThread(): Boolean {
        return true
    }
}
