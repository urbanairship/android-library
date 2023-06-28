/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import android.content.Context;

import com.urbanairship.iam.assets.Assets;
import com.urbanairship.util.Network;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
            return Network.shared().isConnected(context);
        }

        return true;
    }

    @Nullable
    public Assets getAssets() {
        return assets;
    }

}
