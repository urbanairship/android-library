/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.Checks;

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
     * Key to define the URI the link prompt directs to if the user chooses to rate the app.
     */
    static final String STORE_URI_KEY = "store_uri";

    /**
     * URL to the Google Play store.
     */
    private static final String MARKET_PLAY_URL = "market://details?id=";

    /**
     * HTTPS URL to the Google Play store. Used instead of the market URl if the play store is not available.
     */
    private static final String HTTPS_PLAY_URL = "https://play.google.com/store/apps/details?id=";

    /**
     * URL to the Amazon store.
     */
    private static final String AMAZON_URL = "amzn://apps/android?p=";

    /**
     * Intent action for linking directly to store review page or displaying a rating link prompt
     * with the option of opening the review page link.
     */
    @NonNull
    public static final String SHOW_RATE_APP_INTENT_ACTION = "com.urbanairship.actions.SHOW_RATE_APP_INTENT_ACTION";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Uri storeUri = getAppStoreUri();
        Checks.checkNotNull(storeUri, "Missing store URI");

        if (arguments.getValue().toJsonValue().optMap().opt(SHOW_LINK_PROMPT_KEY).getBoolean(false)) {
            startRateAppActivity(storeUri, arguments);
        } else {
            Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, storeUri)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            UAirship.getApplicationContext().startActivity(openLinkIntent);
        }

        return ActionResult.newEmptyResult();
    }

    private void startRateAppActivity(@NonNull Uri storeUri, @NonNull ActionArguments arguments) {

        Context context = UAirship.getApplicationContext();
        JsonMap argMap = arguments.getValue().toJsonValue().optMap();

        final Intent intent = new Intent(SHOW_RATE_APP_INTENT_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setPackage(UAirship.getPackageName())
                .putExtra(STORE_URI_KEY, storeUri);

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
                return getAppStoreUri() != null;
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

    @Nullable
    private Uri getAppStoreUri() {
        UAirship airship = UAirship.shared();
        if (airship.getAirshipConfigOptions().appStoreUri != null) {
            return airship.getAirshipConfigOptions().appStoreUri;
        }

        String packageName = UAirship.getApplicationContext().getPackageName();

        // Get Store Uri for platform
        if (UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM) {
            return Uri.parse(AMAZON_URL + packageName);
        }

        if (UAirship.shared().getPlatformType() == UAirship.ANDROID_PLATFORM) {
            if (PlayServicesUtils.isGooglePlayStoreAvailable(UAirship.getApplicationContext())) {
                return Uri.parse(MARKET_PLAY_URL + packageName);
            } else {
                return Uri.parse(HTTPS_PLAY_URL + packageName);
            }
        }

        return null;
    }

}

