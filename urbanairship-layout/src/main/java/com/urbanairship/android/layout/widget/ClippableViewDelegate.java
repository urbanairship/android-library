/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

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
    void setClipPathBorderRadius(@NonNull View view, @Dimension float borderRadius) {

        if (borderRadius == 0) {
            view.setClipToOutline(false);
            view.setOutlineProvider(ViewOutlineProvider.BOUNDS);
        } else {
            view.setClipToOutline(true);
            view.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(@NonNull View view, @NonNull Outline outline) {
                    outline.setRoundRect(0, 0, view.getRight() - view.getLeft(), view.getBottom() - view.getTop(), borderRadius);
                }
            });
        }
    }
}
