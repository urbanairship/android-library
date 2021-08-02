/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.base.Supplier;
import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UriUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

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

    private final Supplier<UAirship> airshipSupplier;

    public DeepLinkAction() {
        this(new Supplier<UAirship>() {
            @Override
            public UAirship get() {
                return UAirship.shared();
            }
        });
    }

    @VisibleForTesting
    DeepLinkAction(@NonNull Supplier<UAirship> airshipSupplier) {
        this.airshipSupplier = airshipSupplier;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        String deepLink = arguments.getValue().getString();
        UAirship airship = airshipSupplier.get();
        Checks.checkNotNull(deepLink, "Missing feature.");
        Checks.checkNotNull(airship, "Missing airship.");

        Logger.info("Deep linking: %s", deepLink);
        if (!airship.deepLink(deepLink)) {
            // Fallback to intent launching
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(deepLink))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setPackage(UAirship.getPackageName());

            PushMessage message = arguments.getMetadata().getParcelable(ActionArguments.PUSH_MESSAGE_METADATA);
            if (message != null) {
                intent.putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle());
            }

            UAirship.getApplicationContext().startActivity(intent);
        }

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
                return arguments.getValue().getString() != null;

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
