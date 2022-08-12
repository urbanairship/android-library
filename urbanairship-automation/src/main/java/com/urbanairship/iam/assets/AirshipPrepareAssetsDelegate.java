/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import android.graphics.BitmapFactory;

import com.urbanairship.Logger;
import com.urbanairship.android.layout.util.UrlInfo;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaInfo;
import com.urbanairship.iam.banner.BannerDisplayContent;
import com.urbanairship.iam.fullscreen.FullScreenDisplayContent;
import com.urbanairship.iam.layout.AirshipLayoutDisplayContent;
import com.urbanairship.iam.modal.ModalDisplayContent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.FileUtils;
import com.urbanairship.util.UAHttpStatusUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    public void onSchedule(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull Assets assets) {
        onPrepare(scheduleId, message, assets);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @AssetManager.PrepareResult
    public int onPrepare(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull Assets assets) {
        List<String> cacheableUrls = getCacheableUrls(message);
        for (String url : cacheableUrls) {
            if (assets.file(url).exists()) {
                continue;
            }

            try {
                FileUtils.DownloadResult result = cacheImage(assets, url);
                if (!result.isSuccess) {
                    if (UAHttpStatusUtil.inClientErrorRange(result.statusCode)) {
                        return AssetManager.PREPARE_RESULT_CANCEL;
                    }

                    return AssetManager.PREPARE_RESULT_RETRY;
                }
            } catch (Exception e) {
                Logger.error(e, "Unable to download file: %s ", url);
                return AssetManager.PREPARE_RESULT_RETRY;
            }
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
     * Helper method that parses all the cachable urls.
     *
     * @param message The message.
     * @return The list of cachable urls.
     */
    @NonNull
    private List<String> getCacheableUrls(@NonNull InAppMessage message) {
        switch (message.getType()) {
            case InAppMessage.TYPE_BANNER:
                BannerDisplayContent bannerDisplayContent = message.getDisplayContent();
                if (bannerDisplayContent != null) {
                    String url = getCacheableUrl(bannerDisplayContent.getMedia());
                    if (url != null) {
                        return Collections.singletonList(url);
                    }
                }
                break;

            case InAppMessage.TYPE_FULLSCREEN:
                FullScreenDisplayContent fullScreenDisplayContent = message.getDisplayContent();
                if (fullScreenDisplayContent != null) {
                    String url = getCacheableUrl(fullScreenDisplayContent.getMedia());
                    if (url != null) {
                        return Collections.singletonList(url);
                    }
                }
                break;

            case InAppMessage.TYPE_MODAL:
                ModalDisplayContent modalDisplayContent = message.getDisplayContent();
                if (modalDisplayContent != null) {
                    String url = getCacheableUrl(modalDisplayContent.getMedia());
                    if (url != null) {
                        return Collections.singletonList(url);
                    }
                }
                break;

            case InAppMessage.TYPE_AIRSHIP_LAYOUT:
                AirshipLayoutDisplayContent layoutContent = message.getDisplayContent();
                if (layoutContent != null) {

                    List<String> cacheableUrls = new ArrayList<>();
                    for (UrlInfo urlInfo : UrlInfo.from(layoutContent.getPayload().getView())) {
                        if (urlInfo.getType() == UrlInfo.UrlType.IMAGE) {
                            cacheableUrls.add(urlInfo.getUrl());
                        }
                    }
                    return cacheableUrls;
                }
        }

        return Collections.emptyList();
    }

    @Nullable
    public static String getCacheableUrl(@Nullable MediaInfo mediaInfo) {
        if (mediaInfo != null && mediaInfo.getType().equals(MediaInfo.TYPE_IMAGE)) {
            return mediaInfo.getUrl();
        } else {
            return null;
        }
    }

}
