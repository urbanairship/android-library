/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import android.graphics.BitmapFactory;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.Logger;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageSchedule;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.iam.modal.ModalDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.FileUtils;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Default {@link PrepareAssetsDelegate} for Airship message types.
 */
public class AirshipPrepareAssetsDelegate implements PrepareAssetsDelegate {

    /**
     * Cache key for the image width.
     */
    @NonNull
    public static final String IMAGE_WIDTH_CACHE_KEY = "width";

    /**
     * Cache key for the image height.
     */
    @NonNull
    public static final String IMAGE_HEIGHT_CACHE_KEY = "height";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSchedule(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessage message, @NonNull Assets assets) {
        onPrepare(schedule, message, assets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @AssetManager.PrepareResult
    public int onPrepare(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessage message, @NonNull Assets assets) {
        MediaInfo mediaInfo = getMediaInfo(message);
        if (mediaInfo == null || !MediaInfo.TYPE_IMAGE.equals(mediaInfo.getType()) || assets.file(mediaInfo.getUrl()).exists()) {
            return AssetManager.PREPARE_RESULT_OK;
        }

        try {
            FileUtils.DownloadResult result = cacheImage(assets, mediaInfo.getUrl());
            if (!result.isSuccess) {
                if (UAHttpStatusUtil.inClientErrorRange(result.statusCode)) {
                    return AssetManager.PREPARE_RESULT_CANCEL;
                }

                return AssetManager.PREPARE_RESULT_RETRY;
            }
        } catch (IOException e) {
            Logger.error(e, "Unable to download file: %s ", mediaInfo.getUrl());
            return AssetManager.PREPARE_RESULT_RETRY;
        }

        return AssetManager.PREPARE_RESULT_OK;
    }

    /**
     * Helper method that caches an image in the assets.
     *
     * @param assets The assets.
     * @param url The image URL.
     * @return The download result.
     * @throws IOException If the URL is invalid.
     */
    @NonNull
    protected FileUtils.DownloadResult cacheImage(@NonNull Assets assets, @NonNull String url) throws IOException {
        File file = assets.file(url);
        FileUtils.DownloadResult result = FileUtils.downloadFile(new URL(url), file);

        if (result.isSuccess) {
            // Cache the width and height for view resizing
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            assets.setMetadata(url, JsonMap.newBuilder()
                                           .putOpt(IMAGE_WIDTH_CACHE_KEY, options.outWidth)
                                           .putOpt(IMAGE_HEIGHT_CACHE_KEY, options.outHeight)
                                           .build());
        }

        return result;
    }

    /**
     * Helper method that parses the media info from an {@link InAppMessage}
     *
     * @param message The message.
     * @return The media info if set, otherwise {@code null}.
     */
    @Nullable
    private MediaInfo getMediaInfo(@NonNull InAppMessage message) {
        switch (message.getType()) {
            case InAppMessage.TYPE_BANNER:
                BannerDisplayContent bannerDisplayContent = message.getDisplayContent();
                if (bannerDisplayContent != null) {
                    return bannerDisplayContent.getMedia();
                }
                break;

            case InAppMessage.TYPE_FULLSCREEN:
                FullScreenDisplayContent fullScreenDisplayContent = message.getDisplayContent();
                if (fullScreenDisplayContent != null) {
                    return fullScreenDisplayContent.getMedia();
                }
                break;

            case InAppMessage.TYPE_MODAL:
                ModalDisplayContent modalDisplayContent = message.getDisplayContent();
                if (modalDisplayContent != null) {
                    return modalDisplayContent.getMedia();
                }
                break;
        }

        return null;

    }

}
