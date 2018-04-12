/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;

/**
 * Action to link users to the rating section of their respective app store directly or through a prompt.
 *
 * <p/>
 * Accepted situations: SITUATION_MANUAL_INVOCATION, SITUATION_WEB_VIEW_INVOCATION, SITUATION_PUSH_OPENED,
 * SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Expected argument values:
 * ``show_link_prompt``:Required Boolean. If NO action will link directly to the Amazon or Play store
 * review page, if YES action will display a rating prompt. Defaults to NO if null.
 * ``title``: Optional String. String to override the link prompt's title.
 *   Title over 50 characters will be rejected. Header defaults to "Enjoying <App Name>?" if null.
 * ``body``: Optional String. String to override the link prompt's body.
 *  Prompt bodies over 100 characters will be rejected. Body defaults to "Tap Rate to rate it in the
 *  app store." if null.
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^ra, rate_app_action
 * <p/>
 */

public class RateAppAction extends Action {

    /**
     * Maximum character length of link prompt title
     */
    private static final int TITLE_MAX_CHARS = 50;

    /**
     * Maximum character length of link prompt body
     */
    private static final int BODY_MAX_CHARS = 100;

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "rate_app_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^ra";

    /**
     * Key for the showing link prompt option.
     */
    public static final String SHOW_LINK_PROMPT_KEY = "show_link_prompt";

    /**
     * Key to define the app review link prompt's title when providing the action's value as a map.
     */
    public static final String TITLE_KEY = "title";

    /**
     * Key to define the app review link prompt's body when providing the action's value as a map.
     */
    public static final String BODY_KEY = "body";

    /**
     * Key to define the URI the link prompt directs to if the user chooses to rate the app.
     */
    static final String STORE_URI_KEY = "store_uri";

    /**
     * URL to the Google Play store.
     */
    private static final String PLAY_URL = "market://details?id=";

    /**
     * URL to the Amazon store.
     */
    private static final String AMAZON_URL = "amzn://apps/android?p=";

    /**
     * Intent action for linking directly to store review page or displaying a rating link prompt
     * with the option of opening the review page link.
     */
    public static final String SHOW_RATE_APP_INTENT_ACTION = "com.urbanairship.actions.SHOW_RATE_APP_INTENT_ACTION";

    private Boolean showLinkPrompt;
    private String title;
    private String body;
    private Uri storeUri;

    private String defaultTitle;
    private String defaultBody;

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        if (!parseArguments(arguments)) {
            return ActionResult.newEmptyResultWithStatus(ActionResult.STATUS_REJECTED_ARGUMENTS);
        }

        if (showLinkPrompt == true) {
            final Intent actionIntent = new Intent(SHOW_RATE_APP_INTENT_ACTION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(SHOW_LINK_PROMPT_KEY, showLinkPrompt)
                    .setPackage(UAirship.getPackageName());

            actionIntent.putExtra(STORE_URI_KEY, storeUri.toString());

            if (title != null) {
                actionIntent.putExtra(TITLE_KEY, title);
            } else {
                actionIntent.putExtra(TITLE_KEY, defaultTitle);
            }

            if (body != null) {
                actionIntent.putExtra(BODY_KEY, body);
            } else {
                actionIntent.putExtra(BODY_KEY, defaultBody);
            }

            try {
                UAirship.getApplicationContext().startActivity(actionIntent);
            } catch (ActivityNotFoundException e) {
                Logger.error("Unable to start Rate App Action activity.");
            }
        } else {
            try {
                Intent openLinkIntent = new Intent(Intent.ACTION_VIEW, storeUri);
                UAirship.getApplicationContext().startActivity(openLinkIntent);
            } catch (ActivityNotFoundException e) {
                Logger.error("No web browser available to handle request to open the store link.");
            }
        }

        return ActionResult.newEmptyResult();
    }

    private String getAppName() {
        String packageName = UAirship.getApplicationContext().getPackageName();
        PackageManager packageManager = UAirship.getApplicationContext().getPackageManager();

        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            String appName = (String)packageManager.getApplicationLabel(info);
            return appName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    protected boolean parseArguments(@NonNull ActionArguments arguments) {
        final Context context = UAirship.getApplicationContext();

        String positiveButtonTitle = context.getString(R.string.ua_rate_app_action_default_rate_positive_button);
        defaultTitle = context.getString(R.string.ua_rate_app_action_default_title, getAppName());
        defaultBody =  context.getString(R.string.ua_rate_app_action_default_body, positiveButtonTitle);

        String packageName = UAirship.getApplicationContext().getPackageName();

        // Get Store Uri for platform
        if (UAirship.shared().getPlatformType() == UAirship.AMAZON_PLATFORM) {
            storeUri = Uri.parse(AMAZON_URL + packageName);
        }

        if (UAirship.shared().getPlatformType() == UAirship.ANDROID_PLATFORM) {
            storeUri = Uri.parse(PLAY_URL + packageName);
        }

        // Bail if store Uri is somehow unavailable
        if (storeUri == null) {
            Logger.error("App store for this platform could not be determined.");
            return false;
        }

        JsonMap argumentsMap = arguments.getValue().getMap();
        if (argumentsMap == null) {
            return false;
        }

        // Show link prompt flag is required
        if (!argumentsMap.opt(SHOW_LINK_PROMPT_KEY).isBoolean()) {
            Logger.error("Option to show link prompt must be specified.");
            return false;
        }

        // If a title is provided - it must be less than or equal to TITLE_MAX_CHARS in length
        if (argumentsMap.opt(TITLE_KEY).isString() && argumentsMap.opt(TITLE_KEY).toString().length() > TITLE_MAX_CHARS) {
            Logger.error("Rate App Action link prompt title cannot be greater than 50 chars in length.");
            return false;
        }

        // If a body is provided - it must be less than or equal to BODY_MAX_CHARS in length
        if (argumentsMap.opt(BODY_KEY).isString() && argumentsMap.opt(BODY_KEY).toString().length() > BODY_MAX_CHARS) {
            Logger.error("Rate App Action link prompt body cannot be greater than 100 chars in length.");
            return false;
        }

        showLinkPrompt = argumentsMap.opt(SHOW_LINK_PROMPT_KEY).getBoolean(false);
        title = argumentsMap.opt(TITLE_KEY).getString();
        body = argumentsMap.opt(BODY_KEY).getString();

        return true;
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
                return parseArguments(arguments);
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

