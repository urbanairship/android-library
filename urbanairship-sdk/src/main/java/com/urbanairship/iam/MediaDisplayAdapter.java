/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.app.Activity;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.js.Whitelist;
import com.urbanairship.util.FileUtils;
import com.urbanairship.util.Network;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Display adapter that handles caching an in-app message.
 */
public abstract class MediaDisplayAdapter implements InAppMessageAdapter {

    private final static String IMAGE_FILE_NAME = "image";

    private final InAppMessage message;
    private final MediaInfo mediaInfo;
    private InAppMessageCache cache;

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     * @param mediaInfo The media info.
     */
    protected MediaDisplayAdapter(InAppMessage message, MediaInfo mediaInfo) {
        this.message = message;
        this.mediaInfo = mediaInfo;
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        if (mediaInfo == null) {
            return OK;
        }

        if (MediaInfo.TYPE_IMAGE.equals(mediaInfo.getType())) {
            return cacheMedia(context, mediaInfo);
        }

        // Video URLs, check whitelist
        if (!UAirship.shared().getWhitelist().isWhitelisted(mediaInfo.getUrl(), Whitelist.SCOPE_OPEN_URL)) {
            Logger.error("URL not whitelisted. Unable to load: " + mediaInfo.getUrl());
            return CANCEL;
        }

        // Videos require network
        return Network.isConnected() ? OK : RETRY;

    }

    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        if (mediaInfo == null || MediaInfo.TYPE_IMAGE.equals(mediaInfo.getType())) {
            return true;
        } else {
            return Network.isConnected();
        }
    }

    @Override
    @CallSuper
    public void onFinish() {
        if (cache != null) {
            cache.delete();
        }
    }

    /**
     * Gets the cache.
     *
     * @return The cache.
     */
    @Nullable
    protected InAppMessageCache getCache() {
        return cache;
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

    /**
     * Creates the cache.
     *
     * @param context The application context.
     * @throws IOException If the cache fails to create.
     */
    protected void createCache(Context context) throws IOException {
        if (cache == null) {
            cache = InAppMessageCache.newCache(context, message);
        }
    }

    /**
     * Caches the media info.
     *
     * @param context The application context.
     * @param mediaInfo The media info.
     * @return {@link #OK} if the media was null, did not contain a cacheable resource, or if the
     * resource was cached. {@link #RETRY} if it failed to cache the resource.
     */
    @PrepareResult
    protected int cacheMedia(Context context, MediaInfo mediaInfo) {
        if (mediaInfo == null || !mediaInfo.getType().equals(MediaInfo.TYPE_IMAGE)) {
            return OK;
        }

        try {
            createCache(context);

            File file = cache.file(IMAGE_FILE_NAME);
            FileUtils.DownloadResult result = FileUtils.downloadFile(new URL(mediaInfo.getUrl()), file);

            if (!result.isSuccess) {
                if (UAHttpStatusUtil.inClientErrorRange(result.statusCode)) {
                    return CANCEL;
                }

                return RETRY;
            }
            cache.getBundle().putString(InAppMessageCache.MEDIA_CACHE_KEY, Uri.fromFile(file).toString());

            // Cache the width and height for view resizing
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            cache.getBundle().putInt(InAppMessageCache.IMAGE_WIDTH_CACHE_KEY, options.outWidth);
            cache.getBundle().putInt(InAppMessageCache.IMAGE_HEIGHT_CACHE_KEY, options.outHeight);
            return OK;
        } catch (IOException e) {
            Logger.error("Failed to cache media.", e);
            return RETRY;
        }
    }
}

