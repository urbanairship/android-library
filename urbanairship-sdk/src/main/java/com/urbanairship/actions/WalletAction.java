/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.os.Build;
import android.support.annotation.NonNull;

import com.urbanairship.UAirship;

/**
 * Action for opening Android Pay deep links.
 * <p/>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p/>
 * Accepted argument value types: URL as string
 * <p/>
 * Result value: The URI that was opened.
 * <p/>
 * Default Registration Names: ^w, wallet_action
 * <p/>
 * Default Registration Predicate: none
 */
public class WalletAction extends OpenExternalUrlAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "wallet_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^w";

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        // Only support Android platform
        if (UAirship.shared().getPlatformType() != UAirship.ANDROID_PLATFORM) {
            return false;
        }

        // Android pay is only available for KitKat+
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return false;
        }

        return super.acceptsArguments(arguments);
    }
}