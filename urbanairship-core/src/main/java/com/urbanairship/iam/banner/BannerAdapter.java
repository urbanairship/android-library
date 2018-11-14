package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.support.annotation.NonNull;
import android.view.ViewGroup;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.MediaDisplayAdapter;
import com.urbanairship.util.ManifestUtils;

import java.lang.ref.WeakReference;

/**
 * Banner display adapter.
 */
public class BannerAdapter extends MediaDisplayAdapter {

    /**
     * Metadata an app can use to specify the banner's container ID per activity.
     */
    @NonNull
    public final static String BANNER_CONTAINER_ID = "com.urbanairship.iam.banner.BANNER_CONTAINER_ID";

    private final BannerDisplayContent displayContent;

    private WeakReference<Activity> lastActivity;

    /**
     * Default constructor.
     *
     * @param displayContent The display content.
     * @param message The in-app message.
     */
    protected BannerAdapter(@NonNull InAppMessage message, @NonNull BannerDisplayContent displayContent) {
        super(message, displayContent.getMedia());
        this.displayContent = displayContent;
    }

    /**
     * Creates a new banner adapter.
     *
     * @param message The in-app message.
     * @return The banner adapter.
     * @throws IllegalArgumentException If the message is not a banner in-app message.
     */
    @NonNull
    public static BannerAdapter newAdapter(@NonNull InAppMessage message) {
        BannerDisplayContent displayContent = message.getDisplayContent();
        if (displayContent == null) {
            throw new IllegalArgumentException("Invalid message for adapter: " + message);
        }

        return new BannerAdapter(message, displayContent);
    }


    @Override
    public boolean onDisplay(@NonNull Activity activity, boolean isRedisplay, @NonNull DisplayHandler displayHandler) {
        if (!super.onDisplay(activity, isRedisplay, displayHandler)) {
            return false;
        }

        int id = getContainerId(activity);
        if (id == 0 || activity.findViewById(id) == null || !(activity.findViewById(id) instanceof ViewGroup)) {
            Logger.error("BannerAdapter - Unable to display in-app message. Missing view with id: " + id);
            return false;
        }

        Logger.info("BannerAdapter - Displaying in-app message.");


        BannerView view = new BannerView(activity, displayHandler, displayContent, getCache());
        if (lastActivity == null || lastActivity.get() != activity) {
            if (BannerDisplayContent.PLACEMENT_BOTTOM.equals(displayContent.getPlacement())) {
                view.setAnimations(R.animator.ua_iam_slide_in_bottom, R.animator.ua_iam_slide_out_bottom);
            } else {
                view.setAnimations(R.animator.ua_iam_slide_in_top, R.animator.ua_iam_slide_out_top);
            }
        }

        ViewGroup viewGroup = activity.getWindow().getDecorView().findViewById(id);
        viewGroup.addView(view);
        lastActivity = new WeakReference<>(activity);
        return true;
    }

    /**
     * Gets the Banner fragment's container ID.
     * <p>
     * The default implementation checks the activities metadata for {@link #BANNER_CONTAINER_ID}
     * and falls back to `android.R.id.content`.
     *
     * @param activity The activity.
     * @return The container ID.
     */
    protected int getContainerId(@NonNull Activity activity) {
        ActivityInfo info = ManifestUtils.getActivityInfo(activity.getClass());
        if (info != null && info.metaData != null) {
            return info.metaData.getInt(BANNER_CONTAINER_ID, android.R.id.content);
        }

        return android.R.id.content;
    }
}
