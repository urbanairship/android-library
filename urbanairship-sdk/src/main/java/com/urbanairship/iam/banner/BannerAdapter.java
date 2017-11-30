package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;
import com.urbanairship.iam.InAppMessageCache;
import com.urbanairship.util.FileUtils;
import com.urbanairship.util.ManifestUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;

/**
 * Banner display adapter.
 */
public class BannerAdapter implements InAppMessageAdapter {

    /**
     * Metadata an app can use to specify the banner's container ID per activity.
     */
    public final static String BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID";

    private final static String IMAGE_FILE_NAME = "banner_image";

    private final InAppMessage message;
    private InAppMessageCache cache;

    /**
     * Default constructor.
     *
     * @param message The in-app message.
     */
    protected BannerAdapter(InAppMessage message) {
        this.message = message;
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        BannerDisplayContent displayContent = message.getDisplayContent();
        if (displayContent.getMedia() == null) {
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
    @AdapterResult
    public int onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        BannerDisplayContent displayContent = message.getDisplayContent();

        int id = getContainerId(activity);
        if (id == 0 || activity.findViewById(id) == null) {
            Logger.info("BannerAdapter - Unable to display in-app message. Missing view with id: " + id);
            return RETRY;
        }

        int enter, exit;
        switch (displayContent.getPlacement()) {
            case BannerDisplayContent.PLACEMENT_TOP:
                enter = R.animator.ua_iam_slide_in_top;
                exit = R.animator.ua_iam_slide_out_top;
                break;
            case BannerDisplayContent.PLACEMENT_BOTTOM:
            default:
                enter = R.animator.ua_iam_slide_in_bottom;
                exit = R.animator.ua_iam_slide_out_bottom;
                break;
        }

        BannerFragment fragment = BannerFragment.newBuilder()
                                                .setDisplayHandler(displayHandler)
                                                .setExitAnimation(exit)
                                                .setInAppMessage(message)
                                                .setCache(cache)
                                                .build();

        Logger.info("BannerAdapter - Displaying in-app message.");

        activity.getFragmentManager().beginTransaction()
                .setCustomAnimations(enter, 0)
                .add(id, fragment)
                .commit();

        return OK;
    }

    @Override
    public void onFinish() {
        if (cache != null) {
            cache.delete();
        }
    }

    /**
     * Gets the Banner fragment's container ID.
     *
     * The default implementation checks the activities metadata for {@link #BANNER_CONTAINER_ID}
     * and falls back to `android.R.id.content`.
     *
     * @param activity The activity.
     * @return The container ID.
     */
    protected int getContainerId(Activity activity) {
        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null) {
            return info.metaData.getInt(BANNER_CONTAINER_ID, android.R.id.content);
        }

        return android.R.id.content;
    }
}
