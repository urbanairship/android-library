/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

/**
 * Displays an inbox message in a landing page.
 * <p/>
 * To view messages, the intent will use the action
 * {@code com.urbanairship.SHOW_LANDING_PAGE_INTENT_ACTION} with the message ID supplied as the data
 * in the form of {@code message:<MESSAGE_ID>}.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.WEB_VIEW_INVOCATION,
 * Situation.MANUAL_INVOCATION, and Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON.
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
            case PUSH_OPENED:
            case WEB_VIEW_INVOCATION:
            case MANUAL_INVOCATION:
            case FOREGROUND_NOTIFICATION_ACTION_BUTTON:
                if (arguments.getValue().getString() == null) {
                    return false;
                }

                if (MESSAGE_ID_PLACEHOLDER.equalsIgnoreCase(arguments.getValue().getString())) {
                    return arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA) ||
                            arguments.getMetadata().containsKey(ActionArguments.PUSH_MESSAGE_METADATA);
                }

                return true;
            case BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        String messageId = arguments.getValue().getString();

        if (messageId.equalsIgnoreCase(MESSAGE_ID_PLACEHOLDER)) {
            PushMessage pushMessage = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
            if (pushMessage != null && pushMessage.getRichPushMessageId() != null) {
                messageId = pushMessage.getRichPushMessageId();
            } else if (arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA)) {
                messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);
            }
        }

        final RichPushMessage message = UAirship.shared().getRichPushManager().getRichPushInbox().getMessage(messageId);
        if (message == null) {
            return ActionResult.newErrorResult(new Exception("Unable to find message with ID " + messageId));
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION)
                        .setPackage(UAirship.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, message.getMessageId(), null));

                try {
                    UAirship.getApplicationContext().startActivity(intent);
                } catch (ActivityNotFoundException ex) {
                    Logger.error("Unable to view the inbox message in a landing page. The landing page activity " +
                            "is either missing in the manifest or does not include the message scheme in its " +
                            "intent filter.");
                }
            }
        });

        return ActionResult.newEmptyResult();
    }
}
