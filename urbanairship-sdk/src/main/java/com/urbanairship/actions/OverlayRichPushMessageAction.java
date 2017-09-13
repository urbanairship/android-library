/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.util.UAStringUtil;

/**
 * Displays an inbox message in a landing page.
 * <p/>
 * To view messages, the intent will use the action
 * {@code com.urbanairship.SHOW_LANDING_PAGE_INTENT_ACTION} with the message ID supplied as the data
 * in the form of {@code message:<MESSAGE_ID>}.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument values: The specified message ID, or {@code "auto"}
 * to look for the message ID in the {@link ActionArguments#getMetadata()}.
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^mco, open_mc_overlay_action
 */
public class OverlayRichPushMessageAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "open_mc_overlay_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^mco";

    public static final String MESSAGE_ID_PLACEHOLDER = "auto";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                if (UAStringUtil.isEmpty(arguments.getValue().getString())) {
                    return false;
                }

                return !MESSAGE_ID_PLACEHOLDER.equalsIgnoreCase(arguments.getValue().getString()) ||
                        arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA) ||
                        arguments.getMetadata().containsKey(ActionArguments.PUSH_MESSAGE_METADATA);

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

        if (messageId.equalsIgnoreCase(MESSAGE_ID_PLACEHOLDER)) {
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
            return ActionResult.newErrorResult(new Exception("Missing message ID."));
        }

        Intent intent = new Intent(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION)
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, messageId, null));

        try {
            UAirship.getApplicationContext().startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            Logger.error("Unable to view the inbox message in a landing page. The landing page activity " +
                    "is either missing in the manifest or does not include the message scheme in its " +
                    "intent filter.");

            return ActionResult.newErrorResult(ex);
        }

        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

}
