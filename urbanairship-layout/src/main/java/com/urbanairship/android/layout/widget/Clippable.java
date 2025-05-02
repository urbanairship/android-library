/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.widget;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.RequiresApi;

public interface Clippable {
    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    void setClipPathBorderRadius(@Dimension float borderRadius);

    /**
     * Clips the view to the border with different corner radii.
     *
     * @param borderRadii The border radius.
     */
    @RequiresApi(api = 30)
    @MainThread
    void setClipPathBorderRadius(@Dimension float[] borderRadii);
}
