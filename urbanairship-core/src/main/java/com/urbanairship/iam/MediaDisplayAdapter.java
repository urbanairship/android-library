/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.iam.assets.Assets;
import com.urbanairship.js.Whitelist;
import com.urbanairship.util.Network;

/**
 * Display adapter that handles caching an in-app message.
 */
public abstract class MediaDisplayAdapter extends ForegroundDisplayAdapter {

    private final InAppMessage message;
    private final MediaInfo mediaInfo;
    private Assets assets;

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     * @param mediaInfo The media info.
     */
    protected MediaDisplayAdapter(@NonNull InAppMessage message, @Nullable MediaInfo mediaInfo) {
        this.message = message;
        this.mediaInfo = mediaInfo;
    }

    @Override
    @PrepareResult
    public int onPrepare(@NonNull Context context, @NonNull Assets assets) {
        this.assets = assets;
        if (mediaInfo == null) {
            return OK;
        }

        if (!MediaInfo.TYPE_IMAGE.equals(mediaInfo.getType()) && isWhiteListed(mediaInfo.getUrl())) {
            Logger.error("URL not whitelisted. Unable to load: %s", mediaInfo.getUrl());
            return CANCEL;
        }

        return OK;
    }

    @Override
    @CallSuper
    public void onFinish(@NonNull Context context) {
    }

    /**
     * Gets the in-app message.
     *
     * @return The in-app message.
     */
    @NonNull
    protected InAppMessage getMessage() {
        return message;
    }

    @CallSuper
    @Override
    public boolean isReady(@NonNull Context context) {
        if (!super.isReady(context)) {
            return false;
        }

        if (mediaInfo == null) {
            return true;
        }

        if (assets == null || !assets.file(mediaInfo.getUrl()).exists()) {
            return Network.isConnected();
        }

        return true;
    }

    @Nullable
    public Assets getAssets() {
        return assets;
    }

    /**
     * Checks if a URL is whitelisted.
     *
     * @param url The URL.
     * @return {@code true} if whitelisted, otherwise {@code false}.
     */
    private static boolean isWhiteListed(String url) {
        return UAirship.shared().getWhitelist().isWhitelisted(url, Whitelist.SCOPE_OPEN_URL);
    }

}

