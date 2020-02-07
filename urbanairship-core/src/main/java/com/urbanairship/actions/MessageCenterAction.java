/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import androidx.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.UAStringUtil;

/**
 * Starts an activity to display either the {@link RichPushInbox} or a {@link RichPushMessage} using
 * either {@link RichPushInbox#startInboxActivity()} or {@link RichPushInbox#startMessageActivity(String)}.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument values: {@code null} to launch the inbox, the specified message ID, or {@code "auto"}
 * to look for the message ID in the {@link ActionArguments#getMetadata()}.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: ^mc, open_mc_action
 */
public class MessageCenterAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "open_mc_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^mc";

    /**
     * Overlay message center registry name
     *
     * @deprecated May be removed in a future SDK version. Use "open_mc_action" instead.
     */
    @NonNull
    @Deprecated
    public static final String REGISTRY_NAME_OVERLAY = "open_mc_overlay_action";

    /**
     * Overlay message center registry short name
     *
     * @deprecated May be removed in a future SDK version. Use "^mc" instead.
     */
    @NonNull
    @Deprecated
    public static final String REGISTRY_SHORT_NAME_OVERLAY = "^mco";

    /**
     * Message ID place holder. Will pull the message ID from the push metadata.
     */
    @NonNull
    public static final String MESSAGE_ID_PLACEHOLDER = "auto";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                return true;

            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        String messageId = arguments.getValue().getString();

        if (MESSAGE_ID_PLACEHOLDER.equalsIgnoreCase(messageId)) {
            PushMessage pushMessage = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
            if (pushMessage != null && pushMessage.getRichPushMessageId() != null) {
                messageId = pushMessage.getRichPushMessageId();
            } else if (arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA)) {
                messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);
            } else {
                messageId = null;
            }
        }

        if (UAStringUtil.isEmpty(messageId)) {
            UAirship.shared().getMessageCenter().showMessageCenter();

        } else {
            UAirship.shared().getMessageCenter().showMessageCenter(messageId);
        }

        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

}
