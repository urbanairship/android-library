/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.actions

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.push.PushMessage
import com.urbanairship.util.AirshipComponentUtils.callableForComponent
import com.urbanairship.util.UAStringUtil
import java.util.concurrent.Callable

/**
 * Starts an activity to display either the [Inbox] or a [Message] using
 * either [MessageCenter.showMessageCenter] or [MessageCenter.showMessageCenter].
 *
 * **Accepted situations:**
 * - SITUATION_PUSH_OPENED
 * - SITUATION_WEB_VIEW_INVOCATION
 * - SITUATION_MANUAL_INVOCATION
 * - SITUATION_AUTOMATION
 * - SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 *
 * **Accepted argument values:** `null` to launch the inbox,the specified message ID to display,
 * or `"auto"` to look for the message ID in the [ActionArguments.getMetadata].
 *
 * **Result value:** `null`
 *
 * **Default Registration Names:** `"^mc"`, `"open_mc_action"`
 */
public open class MessageCenterAction
@VisibleForTesting internal constructor(
    private val messageCenterCallable: Callable<MessageCenter>
) : Action() {

    public constructor() : this(
        messageCenterCallable = callableForComponent(MessageCenter::class.java)
    )

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION, SITUATION_MANUAL_INVOCATION, SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_AUTOMATION -> true
            SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON, SITUATION_PUSH_RECEIVED -> false
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val messageCenter: MessageCenter = try {
            messageCenterCallable.call()
        } catch (e: Exception) {
            return ActionResult.newErrorResult(e)
        }

        var messageId = arguments.value.string
        if (MESSAGE_ID_PLACEHOLDER.equals(messageId, ignoreCase = true)) {
            val pushMessage =
                arguments.metadata.getParcelable<PushMessage>(ActionArguments.PUSH_MESSAGE_METADATA)

            messageId = if (pushMessage != null && pushMessage.richPushMessageId != null) {
                pushMessage.richPushMessageId
            } else if (arguments.metadata.containsKey(ActionArguments.RICH_PUSH_ID_METADATA)) {
                arguments.metadata.getString(ActionArguments.RICH_PUSH_ID_METADATA)
            } else {
                null
            }
        }

        if (UAStringUtil.isEmpty(messageId)) {
            messageCenter.showMessageCenter()
        } else {
            messageCenter.showMessageCenter(messageId)
        }
        return ActionResult.newEmptyResult()
    }

    override fun shouldRunOnMainThread(): Boolean = true

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {

        /** Default registry name. */
        public const val DEFAULT_REGISTRY_NAME: String = "open_mc_action"

        /** Default registry short name. */
        public const val DEFAULT_REGISTRY_SHORT_NAME: String = "^mc"

        /** Message ID place holder. Will pull the message ID from the push metadata. */
        public const val MESSAGE_ID_PLACEHOLDER: String = "auto"
    }
}
