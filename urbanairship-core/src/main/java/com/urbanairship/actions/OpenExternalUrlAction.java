/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.UrlAllowList;
import com.urbanairship.util.UriUtils;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Supplier;

/**
 * Action for opening a URL for viewing.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument value types: URL as a string
 * <p>
 * Result value: The URI that was opened.
 * <p>
 * Default Registration Names: ^u, open_external_url_action
 * <p>
 * Default Registration Predicate: none
 */
public class OpenExternalUrlAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "open_external_url_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^u";

    private Supplier<UrlAllowList> allowListSupplier;

    /**
     * Default constructor.
     */
    public OpenExternalUrlAction() {
        this(() -> UAirship.shared().getUrlAllowList());
    }

    @VisibleForTesting
    OpenExternalUrlAction(@NonNull Supplier<UrlAllowList> allowListSupplier) {
        this.allowListSupplier = allowListSupplier;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Uri uri = UriUtils.parse(arguments.getValue().getString());

        UALog.i("Opening URI: %s", uri);

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        UAirship.getApplicationContext().startActivity(intent);
        return ActionResult.newResult(arguments.getValue());
    }

    /**
     * The open external URL action accepts Strings that can be parsed as URL argument value types.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_PUSH_OPENED:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_AUTOMATION:
                if (UriUtils.parse(arguments.getValue().getString()) == null) {
                    return false;
                }

                return allowListSupplier.get().isAllowed(arguments.getValue().getString(), UrlAllowList.SCOPE_OPEN_URL);

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
