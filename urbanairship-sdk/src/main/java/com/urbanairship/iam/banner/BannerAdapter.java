package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.DisplayHandler;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Banner display adapter.
 */
public class BannerAdapter implements InAppMessageAdapter {

    private final InAppMessage message;

    /**
     * Default constructor.
     * @param message The in-app message.
     */
    protected BannerAdapter(InAppMessage message) {
        this.message = message;
    }

    @Override
    public int onPrepare(@NonNull Context context) {
        return OK;
    }

    @Override
    @AdapterResult
    public int onDisplay(@NonNull Activity activity, boolean isRedisplay, DisplayHandler displayHandler) {

        BannerDisplayContent displayContent = message.getDisplayContent();

        int enter, exit;
        if (displayContent.getPlacement() == BannerDisplayContent.PLACEMENT_TOP) {
            enter = R.animator.ua_iam_slide_in_top;
            exit = R.animator.ua_iam_slide_out_top;
        } else {
            enter = R.animator.ua_iam_slide_in_bottom;
            exit = R.animator.ua_iam_slide_out_bottom;
        }

        if (activity.findViewById(android.R.id.content) == null) {
            Logger.info("InAppMessageManager - Unable to display in-app message. Missing view with id android.R.id.content.");
            return RETRY;
        }

        BannerFragment fragment = BannerFragment.newBuilder()
                .setDisplayHandler(displayHandler)
                .setExitAnimation(exit)
                .setInAppMessage(message)
                .build();

        Logger.info("InAppMessageManager - Displaying in-app message.");

        activity.getFragmentManager().beginTransaction()
                .setCustomAnimations(enter, 0)
                .add(android.R.id.content, fragment)
                .commit();

        return OK;
    }

    @Override
    public void onFinish() {

    }
}
