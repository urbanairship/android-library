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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.richpush.RichPushMessage;

/**
 * Starts an activity to display either the {@link RichPushInbox} or a {@link RichPushMessage}.
 * <p/>
 * In order to view the inbox, the action will attempt to start an activity with intent action
 * {@code com.urbanairship.VIEW_RICH_PUSH_INBOX}.
 * <p/>
 * To view messages, the intent will use the action
 * {@code com.urbanairship.VIEW_RICH_PUSH_MESSAGE} with the message ID supplied as the data
 * in the form of {@code message:<MESSAGE_ID>}. If an activity is unable to be started, the message
 * will attempt to be displayed in a Landing Page by using the intent action
 * {@link LandingPageAction#SHOW_LANDING_PAGE_INTENT_ACTION}.
 * <p/>
 * Accepted situations: Situation.PUSH_OPENED, Situation.WEB_VIEW_INVOCATION,
 * Situation.MANUAL_INVOCATION, and Situation.FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument values: {@code null} to launch the inbox, the specified message ID, or {@code "MESSAGE_ID"}
 * to look for the message ID in the {@link ActionArguments#getMetadata()}.
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^mc, open_mc_action
 */
public class OpenRichPushInboxAction extends Action {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "open_mc_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^mc";


    public static final String MESSAGE_ID_PLACEHOLDER = "MESSAGE_ID";

    @Override
    public boolean acceptsArguments(ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case PUSH_OPENED:
            case WEB_VIEW_INVOCATION:
            case MANUAL_INVOCATION:
            case FOREGROUND_NOTIFICATION_ACTION_BUTTON:
                return true;
            case BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @Override
    public ActionResult perform(ActionArguments arguments) {

        String messageId = arguments.getValue().getString();

        if (MESSAGE_ID_PLACEHOLDER.equals(MESSAGE_ID_PLACEHOLDER)) {
            PushMessage pushMessage = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
            if (pushMessage != null && pushMessage.getRichPushMessageId() != null) {
                messageId = pushMessage.getRichPushMessageId();
            } else if (arguments.getMetadata().containsKey(ActionArguments.RICH_PUSH_ID_METADATA)) {
                messageId = arguments.getMetadata().getString(ActionArguments.RICH_PUSH_ID_METADATA);
            }
        }

        final RichPushMessage message = UAirship.shared().getRichPushManager().getRichPushInbox().getMessage(messageId);

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (message != null) {
                    startInboxMessageActivity(UAirship.getApplicationContext(), message);
                } else {
                    startInboxActivity(UAirship.getApplicationContext());
                }
            }
        });

        return ActionResult.newEmptyResult();
    }

    /**
     * Called when an activity should be started to view a {@link RichPushMessage}.
     *
     * @param context The application context.
     * @param message The rich push message.
     */
    private void startInboxMessageActivity(Context context, RichPushMessage message) {
        Intent intent = new Intent()
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, message.getMessageId(), null));

        // Try VIEW_MESSAGE_INTENT_ACTION first
        intent.setAction(RichPushInbox.VIEW_MESSAGE_INTENT_ACTION);
        if (intent.resolveActivity(context.getPackageManager()) == null) {

            // Fallback to SHOW_LANDING_PAGE_INTENT_ACTION
            intent.setAction(LandingPageAction.SHOW_LANDING_PAGE_INTENT_ACTION);
            if (intent.resolveActivity(context.getPackageManager()) == null) {

                // Log an error about the missing manifest entry
                Logger.error("Unable to view the inbox message. Add the intent filter to an activity that " +
                        "can handle viewing an inbox message: <intent-filter>" +
                        "<action android:name=\"com.urbanairship.VIEW_RICH_PUSH_MESSAGE\" />" +
                        "<data android:scheme=\"message\"/><category android:name=\"android.intent.category.DEFAULT\" />" +
                        "</intent-filter>");

                return;
            }
        }

        context.startActivity(intent);
    }

    /**
     * Called when an activity should be started to view the {@link RichPushInbox}.
     *
     * @param context The application context.
     */
    private void startInboxActivity(Context context) {
        Intent intent = new Intent(RichPushInbox.VIEW_INBOX_INTENT_ACTION)
                .setPackage(UAirship.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Logger.error("Unable to view the inbox. Add the intent filter to an activity that " +
                    "can handle viewing the inbox: <intent-filter>" +
                    "<action android:name=\"com.urbanairship.VIEW_RICH_PUSH_INBOX\" />" +
                    "<category android:name=\"android.intent.category.DEFAULT\" /></intent-filter>");

            return;
        }

        context.startActivity(intent);
    }
}
