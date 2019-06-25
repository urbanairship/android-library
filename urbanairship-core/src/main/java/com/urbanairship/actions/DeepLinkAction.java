/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.UriUtils;

/**
 * Action for opening a deep link.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument value types: URL as string
 * <p>
 * Result value: The URI that was opened.
 * <p>
 * Default Registration Names: ^d, deep_link_action
 * <p>
 * Default Registration Predicate: none
 */
public class DeepLinkAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "deep_link_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^d";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Uri uri = UriUtils.parse(arguments.getValue().getString());

        Logger.info("Deep linking: %s", uri);

        DeepLinkListener listener = UAirship.shared().getDeepLinkListener();
        if (listener != null && listener.onDeepLink(uri.toString())) {
            Logger.info("Calling through to deep link listener with uri string: %s", uri.toString());

            return ActionResult.newResult(arguments.getValue());
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setPackage(UAirship.getPackageName());

        PushMessage message = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
        if (message != null) {
            intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle());
        }

        UAirship.getApplicationContext().startActivity(intent);
        return ActionResult.newResult(arguments.getValue());
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                return UriUtils.parse(arguments.getValue().getString()) != null;

            case Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            case Action.SITUATION_PUSH_RECEIVED:
            default:
                return false;
        }
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }

}
