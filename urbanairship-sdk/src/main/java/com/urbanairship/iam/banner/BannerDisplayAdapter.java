package com.urbanairship.iam.banner;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.iam.DisplayArguments;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageAdapter;

/**
 * Banner display adapter.
 */
public class BannerDisplayAdapter implements InAppMessageAdapter {

    @Override
    @AdapterResult
    public int display(@NonNull Activity activity, DisplayArguments displayArguments) {
        if (activity.isFinishing()) {
            Logger.error("InAppMessageManager - Unable to display in-app messages for an activity that is finishing.");
            return RETRY;
        }

        BannerDisplayContent displayContent = displayArguments.getMessage().getDisplayContent();
        int enter, exit;
        if (displayContent.getPosition() == BannerDisplayContent.POSITION_TOP) {
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

        BannerFragment fragment = BannerFragment.newInstance(displayArguments, exit);

        Logger.info("InAppMessageManager - Displaying in-app message.");

        activity.getFragmentManager().beginTransaction()
                .setCustomAnimations(enter, 0)
                .add(android.R.id.content, fragment)
                .commit();

        return OK;
    }

    @Override
    @AdapterResult
    public int prefetchAssets(Context context, InAppMessage message, Bundle assets) {
        return OK;
    }

    @Override
    public boolean acceptsMessage(InAppMessage message) {
        BannerDisplayContent displayContent = message.getDisplayContent();
        return displayContent != null;
    }
}
