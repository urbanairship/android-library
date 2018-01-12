package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;
import com.urbanairship.util.ManifestUtils;

/**
 * Banner display adapter.
 */
public class BannerAdapter extends MediaDisplayAdapter {

    /**
     * Metadata an app can use to specify the banner's container ID per activity.
     */
    public final static String BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID";
    private final BannerDisplayContent displayContent;

    /**
     * Default constructor.
     *
     * @param displayContent The display content.
     * @param message The in-app message.
     */
    protected BannerAdapter(InAppMessage message, BannerDisplayContent displayContent) {
        super(message, displayContent.getMedia());
        this.displayContent = displayContent;
    }

    /**
     * Creates a new banner adapter.
     * @param message The in-app message.
     * @return The banner adapter.
     */
    public static BannerAdapter newAdapter(InAppMessage message) {
        BannerDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new BannerAdapter(message, displayContent);
    }


    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {
        if (!super.onDisplay(activity, isRedisplay, displayHandler)) {
            return false;
        }

        int id = getContainerId(activity);
        if (id == 0 || activity.findViewById(id) == null) {
            Logger.info("BannerAdapter - Unable to display in-app message. Missing view with id: " + id);
            return false;
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
                                                .setInAppMessage(getMessage())
                                                .setCache(getCache())
                                                .build();

        Logger.info("BannerAdapter - Displaying in-app message.");

        activity.getFragmentManager().beginTransaction()
                .setCustomAnimations(enter, 0)
                .add(id, fragment)
                .commit();

        return true;
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
