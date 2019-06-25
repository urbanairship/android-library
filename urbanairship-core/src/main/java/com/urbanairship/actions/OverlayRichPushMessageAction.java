/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.messagecenter.MessageCenter;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UAStringUtil;

/**
 * Schedules an inbox-message to display ASAP.
 *
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 *
 * Accepted argument values: The specified message ID, or {@code "auto"}
 * to look for the message ID in the {@link ActionArguments#getMetadata()}.
 *
 * Result value: <code>null</code>
 *
 * Default Registration Names: ^mco, open_mc_overlay_action
 */
public class OverlayRichPushMessageAction extends LandingPageAction {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "open_mc_overlay_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^mco";

    /**
     * Message ID place holder. Will pull the message ID from the push metadata.
     */
    @NonNull
    public static final String MESSAGE_ID_PLACEHOLDER = "auto";

    @Nullable
    @Override
    protected Uri parseUri(@NonNull ActionArguments arguments) {
        String messageId;
        if (arguments.getValue().getMap() != null) {
            messageId = arguments.getValue().getMap().opt(URL_KEY).getString();
        } else {
            messageId = arguments.getValue().getString();
        }

        if (UAStringUtil.isEmpty(messageId)) {
            return null;
        }

        if (messageId.equalsIgnoreCase(MESSAGE_ID_PLACEHOLDER)) {
            PushMessage pushMessage = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
            if (pushMessage != null && pushMessage.getRichPushMessageId() != null) {
                messageId = pushMessage.getRichPushMessageId();
            } else if (arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA)) {
                messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);
            } else {
                return null;
            }
        }

        if (UAStringUtil.isEmpty(messageId)) {
            return null;
        }

        if (messageId.toLowerCase().startsWith(MessageCenter.MESSAGE_DATA_SCHEME)) {
            return Uri.parse(messageId);
        }

        return Uri.fromParts(MessageCenter.MESSAGE_DATA_SCHEME, messageId, null);
    }

}
