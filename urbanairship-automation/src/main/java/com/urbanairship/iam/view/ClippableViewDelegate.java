/* Copyright Airship and Contributors */

package com.urbanairship.iam.view;

import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * View delegate that supports clipping a view to a border radius.
 */
class ClippableViewDelegate {

    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    void setClipPathBorderRadius(@NonNull View view, final float borderRadius) {
        final float borderRadiusPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, borderRadius, view.getResources().getDisplayMetrics());

        if (borderRadiusPixels == 0) {
            view.setClipToOutline(false);
            view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        } else {
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(@NonNull View view, @NonNull Outline outline) {
                    outline.setRoundRect(0,
                            0,
                            view.getRight() - view.getLeft(),
                            view.getBottom() - view.getTop(),
                            borderRadiusPixels);
                }
            });
        }
    }

}
