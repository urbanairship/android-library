/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.html;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.fullscreen.FullScreenActivity;
import com.urbanairship.util.Network;

/**
 * Html display adapter.
 */
public class HtmlDisplayAdapter implements InAppMessageAdapter {

    private final InAppMessage message;

    /**
     * Default constructor.
     *
     * @param message The HTML in-app message.
     */
    protected HtmlDisplayAdapter(InAppMessage message) {
        this.message = message;
    }

    /**
     * Creates a new modal adapter.
     * @param message The in-app message.
     * @return The modal adapter.
     */
    public static HtmlDisplayAdapter newAdapter(InAppMessage message) {
        HtmlDisplayAdapter displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new HtmlDisplayAdapter(message);
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        return Network.isConnected() ? OK : RETRY;
    }

    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        if (!Network.isConnected()) {
            return false;
        }

        Intent intent = new Intent(activity, HtmlActivity.class)
                .putExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(FullScreenActivity.IN_APP_MESSAGE_KEY, message);

        activity.startActivity(intent);

        return true;
    }

    @Override
    public void onFinish() {}
}
