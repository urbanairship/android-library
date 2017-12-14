/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.modal;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;

import com.urbanairship.iam.CachingDisplayAdapter;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;

/**
 * Modal adapter.
 */
public class ModalAdapter extends CachingDisplayAdapter {

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     */
    protected ModalAdapter(InAppMessage message) {
        super(message);
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        ModalDisplayContent displayContent = getMessage().getDisplayContent();
        return cacheMedia(context, displayContent.getMedia());
    }

    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        Intent intent = new Intent(activity, ModalActivity.class)
                .putExtra(ModalActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(ModalActivity.IN_APP_MESSAGE_KEY, getMessage());

        if (getCache() != null) {
            intent.putExtra(ModalActivity.IN_APP_CACHE_KEY, getCache());
        }

        activity.startActivity(intent);
        return true;
    }
}
