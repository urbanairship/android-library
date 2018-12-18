/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam.modal;


import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;

/**
 * Modal adapter.
 */
public class ModalAdapter extends MediaDisplayAdapter {

    /**
     * Default constructor.
     *
     * @param displayContent The display content.
     * @param message The in-app message.
     */
    private ModalAdapter(@NonNull InAppMessage message, @NonNull ModalDisplayContent displayContent) {
        super(message, displayContent.getMedia());
    }

    /**
     * Creates a new modal adapter.
     *
     * @param message The in-app message.
     * @return The modal adapter.
     */
    @NonNull
    public static ModalAdapter newAdapter(@NonNull InAppMessage message) {
        ModalDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new ModalAdapter(message, displayContent);
    }

    @Override
    public void onDisplay(@NonNull Activity activity, boolean isRedisplay, @NonNull DisplayHandler displayHandler) {
        Intent intent = new Intent(activity, ModalActivity.class)
                .putExtra(ModalActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(ModalActivity.IN_APP_MESSAGE_KEY, getMessage());

        if (getCache() != null) {
            intent.putExtra(ModalActivity.IN_APP_CACHE_KEY, getCache());
        }

        activity.startActivity(intent);
    }
}
