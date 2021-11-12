/*
 Copyright Airship and Contributors
 */

package com.urbanairship.android.layout.widget;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;

public interface Clippable {
    /**
     * Clips the view to the border radius.
     *
     * @param borderRadius The border radius.
     */
    @MainThread
    void setClipPathBorderRadius(@Dimension float borderRadius);
}
