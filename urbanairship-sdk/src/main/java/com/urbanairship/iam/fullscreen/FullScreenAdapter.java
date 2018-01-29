/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.fullscreen;


import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;

/**
 * Full screen adapter.
 */
public class FullScreenAdapter extends MediaDisplayAdapter {


    /**
     * Default constructor.
     *
     * @param displayContent The display content.
     * @param message The in-app message.
     */
    protected FullScreenAdapter(InAppMessage message, FullScreenDisplayContent displayContent) {
        super(message, displayContent.getMedia());
    }

    /**
     * Creates a new full screen adapter.
     * @param message The in-app message.
     * @return The full screen adapter.
     */
    public static FullScreenAdapter newAdapter(InAppMessage message) {
        FullScreenDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new FullScreenAdapter(message, displayContent);
    }


    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        if (!super.onDisplay(activity, isRedisplay, displayHandler)) {
            return false;
        }

        Intent intent = new Intent(activity, FullScreenActivity.class)
                .putExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(FullScreenActivity.IN_APP_MESSAGE_KEY, getMessage());

        if (getCache() != null) {
            intent.putExtra(FullScreenActivity.IN_APP_CACHE_KEY, getCache());
        }

        activity.startActivity(intent);
        return true;
    }
}
