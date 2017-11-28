/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam.fullscreen;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Full screen adapter.
 */
public class FullScreenAdapter implements InAppMessageAdapter {

    private final InAppMessage message;
    private InAppMessageCache cache;
    private final static String IMAGE_FILE_NAME = "image";

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     */
    protected FullScreenAdapter(InAppMessage message) {
        this.message = message;
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        FullScreenDisplayContent displayContent = message.getDisplayContent();

        if (displayContent.getMedia() == null || !displayContent.getMedia().getType().equals(MediaInfo.TYPE_IMAGE)) {
            return OK;
        }

        try {
            if (cache == null) {
                cache = InAppMessageCache.newCache(context, message);
            }

            File file = cache.file(IMAGE_FILE_NAME);
            if (!FileUtils.downloadFile(new URL(displayContent.getMedia().getUrl()), file)) {
                return RETRY;
            }
            cache.getBundle().putString(InAppMessageCache.MEDIA_CACHE_KEY, Uri.fromFile(file).toString());
        } catch (IOException e) {
            return RETRY;
        }

        return OK;
    }

    @Override
    public int onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        Intent intent = new Intent(activity, FullScreenActivity.class)
                .putExtra(FullScreenActivity.DISPLAY_HANDLER_EXTRA_KEY, displayHandler)
                .putExtra(FullScreenActivity.IN_APP_MESSAGE_KEY, message);

        if (cache != null) {
            intent.putExtra(FullScreenActivity.IN_APP_CACHE_KEY, cache);
        }

        activity.startActivity(intent);
        return OK;
    }

    @Override
    public void onFinish() {
        if (cache != null) {
            cache.delete();
        }
    }
}
