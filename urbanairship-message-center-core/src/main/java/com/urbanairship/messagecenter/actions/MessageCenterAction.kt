/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter.actions

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.Airship
import com.urbanairship.actions.Action
import com.urbanairship.actions.ActionArguments
import com.urbanairship.actions.ActionResult
import com.urbanairship.messagecenter.Inbox
import com.urbanairship.messagecenter.Message
import com.urbanairship.messagecenter.MessageCenter
import com.urbanairship.messagecenter.actions.MessageCenterAction.Companion.DEFAULT_NAMES
import com.urbanairship.messagecenter.messageCenter
import com.urbanairship.push.PushMessage
import com.urbanairship.util.UAStringUtil

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
 * or `"auto"` to look for the message ID in the [ActionArguments.metadata].
 *
 * **Result value:** `null`
 *
 * **Default Registration Names:** [DEFAULT_NAMES]
 */
public open class MessageCenterAction
@VisibleForTesting internal constructor(
    private val messageCenterProvider: () -> MessageCenter
) : Action() {

    public constructor() : this(
        messageCenterProvider = { Airship.messageCenter }
    )

    override fun acceptsArguments(arguments: ActionArguments): Boolean {
        return when (arguments.situation) {
            Situation.PUSH_OPENED,
            Situation.WEB_VIEW_INVOCATION,
            Situation.MANUAL_INVOCATION,
            Situation.AUTOMATION,
            Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON -> true
            else -> false
        }
    }

    override fun perform(arguments: ActionArguments): ActionResult {
        val messageCenter: MessageCenter = messageCenterProvider()

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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {

        /** Default registry names. */
        public val DEFAULT_NAMES: Set<String> = setOf("open_mc_action", "^mc", "open_mc_overlay_action", "^mco")

        /** Message ID place holder. Will pull the message ID from the push metadata. */
        public const val MESSAGE_ID_PLACEHOLDER: String = "auto"
    }
}
