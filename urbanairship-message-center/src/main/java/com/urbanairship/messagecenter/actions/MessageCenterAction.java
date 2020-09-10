/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter.actions;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.messagecenter.Inbox;
import com.urbanairship.messagecenter.Message;
import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.AirshipComponentUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.concurrent.Callable;

/**
 * Starts an activity to display either the {@link Inbox} or a {@link Message} using
 * either {@link MessageCenter#showMessageCenter()} ()} or {@link MessageCenter#showMessageCenter(String)}.
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
     * Message ID place holder. Will pull the message ID from the push metadata.
     */
    @NonNull
    public static final String MESSAGE_ID_PLACEHOLDER = "auto";

    private final Callable<MessageCenter> messageCenterCallable;

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

    public MessageCenterAction() {
        this(AirshipComponentUtils.callableForComponent(MessageCenter.class));
    }

    @VisibleForTesting
    MessageCenterAction(@NonNull Callable<MessageCenter>  messageCenterCallable) {
        this.messageCenterCallable = messageCenterCallable;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        MessageCenter messageCenter;
        try {
            messageCenter = messageCenterCallable.call();
        } catch (Exception e) {
            return ActionResult.newErrorResult(e);
        }

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
            messageCenter.showMessageCenter();
        } else {
            messageCenter.showMessageCenter(messageId);
        }

        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

}
