/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.AppStoreUtils;
import com.urbanairship.util.Checks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Action to link users to the rating section of their respective app store directly or through a prompt.
 * <p>
 * <p>
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION, SITUATION_PUSH_OPENED,
 * SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Expected argument values:
 * ``show_link_prompt``: Optional Boolean. If NO action will link directly to the Amazon or Play store
 * review page, if YES action will display a rating prompt. Defaults to NO if null.
 * ``title``: Optional String. String to override the link prompt's title. Header defaults to "Enjoying <App Name>?" if null.
 * ``body``: Optional String. String to override the link prompt's body.
 * Body defaults to "Tap Rate to rate it in the app store." if null.
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: ^ra, rate_app_action
 * <p>
 */

public class RateAppAction extends Action {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "rate_app_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^ra";

    /**
     * Key for the showing link prompt option.
     */
    @NonNull
    public static final String SHOW_LINK_PROMPT_KEY = "show_link_prompt";

    /**
     * Key to define the app review link prompt's title when providing the action's value as a map.
     */
    @NonNull
    public static final String TITLE_KEY = "title";

    /**
     * Key to define the app review link prompt's body when providing the action's value as a map.
     */
    @NonNull
    public static final String BODY_KEY = "body";

    /**
     * Intent action for linking directly to store review page or displaying a rating link prompt
     * with the option of opening the review page link.
     */
    @NonNull
    public static final String SHOW_RATE_APP_INTENT_ACTION = "com.urbanairship.actions.SHOW_RATE_APP_INTENT_ACTION";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        if (arguments.getValue().toJsonValue().optMap().opt(SHOW_LINK_PROMPT_KEY).getBoolean(false)) {
            startRateAppActivity(arguments);
        } else {
            UAirship airship = UAirship.shared();
            Intent openLinkIntent = AppStoreUtils.getAppStoreIntent(UAirship.getApplicationContext(), airship.getPlatformType(), airship.getAirshipConfigOptions())
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            UAirship.getApplicationContext().startActivity(openLinkIntent);
        }

        return ActionResult.newEmptyResult();
    }

    private void startRateAppActivity(@NonNull ActionArguments arguments) {

        Context context = UAirship.getApplicationContext();
        JsonMap argMap = arguments.getValue().toJsonValue().optMap();

        final Intent intent = new Intent(SHOW_RATE_APP_INTENT_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setPackage(UAirship.getPackageName());

        if (argMap.opt(TITLE_KEY).isString()) {
            intent.putExtra(TITLE_KEY, argMap.opt(TITLE_KEY).getString());
        }

        if (argMap.opt(BODY_KEY).isString()) {
            intent.putExtra(BODY_KEY, argMap.opt(BODY_KEY).getString());
        }

        context.startActivity(intent);
    }

    /**
     * Checks if the argument's value can be parsed and ensures the situation is neither
     * Action.SITUATION_PUSH_RECEIVED nor Action.SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON.
     *
     * @param arguments The action arguments.
     * @return <code>true</code> if the action can perform with the arguments,
     * otherwise <code>false</code>.
     */
    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        switch (arguments.getSituation()) {
            case SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON:
            case SITUATION_PUSH_OPENED:
            case SITUATION_MANUAL_INVOCATION:
            case SITUATION_WEB_VIEW_INVOCATION:
            case SITUATION_AUTOMATION:
                return true;
            case SITUATION_PUSH_RECEIVED:
            case SITUATION_BACKGROUND_NOTIFICATION_ACTION_BUTTON:
            default:
                return false;
        }
    }

    @Override
    public boolean shouldRunOnMainThread() {
        return true;
    }
}
