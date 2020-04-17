/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.R;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;

/**
 * Shows a chooser activity to share text.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument values: A String used as the share text.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: ^s, share_action
 */
public class ShareAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "share_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^s";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                return arguments.getValue().getString() != null;

            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        final Context context = UAirship.getApplicationContext();

        Intent sharingIntent = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, arguments.getValue().getString());

        final Intent chooserIntent = Intent.createChooser(sharingIntent, context.getString(R.string.ua_share_dialog_title))
                                           .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        context.startActivity(chooserIntent);

        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

}
