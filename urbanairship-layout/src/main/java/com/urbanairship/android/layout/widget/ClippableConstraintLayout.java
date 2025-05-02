/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ClippableConstraintLayout extends ConstraintLayout implements Clippable {
    private final ClippableViewDelegate clippableViewDelegate;

    public ClippableConstraintLayout(@NonNull Context context) {
        this(context, null);
    }

    public ClippableConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ClippableConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ClippableConstraintLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        clippableViewDelegate = new ClippableViewDelegate();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @MainThread
    public void setClipPathBorderRadius(@Dimension float borderRadius) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @MainThread
    public void setClipPathBorderRadius(float[] borderRadii) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii);
    }
}
