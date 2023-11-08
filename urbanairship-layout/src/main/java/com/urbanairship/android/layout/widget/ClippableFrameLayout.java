/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ClippableFrameLayout extends FrameLayout implements Clippable {
    private final ClippableViewDelegate clippableViewDelegate = new ClippableViewDelegate();

    public ClippableFrameLayout(@NonNull Context context) {
        this(context, null);
    }

    public ClippableFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClippableFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ClippableFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @MainThread
    public void setClipPathBorderRadius(@Dimension float borderRadius) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }
}
