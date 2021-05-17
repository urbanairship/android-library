/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

import androidx.annotation.NonNull;

/**
 * Action for opening Android Pay deep links.
 * <p>
 * Accepted situations: SITUATION_PUSH_OPENED, SITUATION_WEB_VIEW_INVOCATION,
 * SITUATION_MANUAL_INVOCATION, SITUATION_AUTOMATION, and SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON.
 * <p>
 * Accepted argument value types: URL as string
 * <p>
 * Result value: The URI that was opened.
 * <p>
 * Default Registration Names: ^w, wallet_action
 * <p>
 * Default Registration Predicate: none
 */
public class WalletAction extends OpenExternalUrlAction {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "wallet_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^w";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Logger.info("Processing Wallet adaptive link.");

        Intent intent = new Intent(UAirship.getApplicationContext(), WalletLoadingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(Uri.parse(arguments.getValue().getString()));
        UAirship.getApplicationContext().startActivity(intent);
        return ActionResult.newEmptyResult();
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        // Only support Android platform
        if (UAirship.shared().getPlatformType() != UAirship.ANDROID_PLATFORM) {
            return false;
        }

        return super.acceptsArguments(arguments);
    }
}
