/* Copyright Airship and Contributors */

package com.urbanairship.iam.fullscreen;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;

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
    protected FullScreenAdapter(@NonNull InAppMessage message, @NonNull FullScreenDisplayContent displayContent) {
        super(message, displayContent.getMedia());
    }

    /**
     * Creates a new full screen adapter.
     *
     * @param message The in-app message.
     * @return The full screen adapter.
     */
    @NonNull
    public static FullScreenAdapter newAdapter(@NonNull InAppMessage message) {
        FullScreenDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new FullScreenAdapter(message, displayContent);
    }

    @Override
    public void onDisplay(@NonNull Context context, @NonNull DisplayHandler displayHandler) {
        Intent intent = new Intent(context, FullScreenActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(FullScreenActivity.IN_APP_MESSAGE_KEY, getMessage())
                .putExtra(FullScreenActivity.IN_APP_ASSETS, getAssets());

        context.startActivity(intent);
    }

}
