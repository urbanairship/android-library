/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.widget;

import android.content.Context;
import android.widget.FrameLayout;

import com.urbanairship.android.layout.property.ConstrainedSize;

import androidx.annotation.Dimension;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

/**
 * FrameLayout that supports min and max dimension constraints.
 *
 * @hide
 */
public class ConstrainedFrameLayout extends FrameLayout implements Clippable {

    private final ClippableViewDelegate clippableViewDelegate;
    private final ConstrainedViewDelegate constrainedViewDelegate;

    /**
     * Default constructor.
     *
     * @param context A Context object used to access application assets.
     */
    public ConstrainedFrameLayout(@NonNull Context context, @NonNull ConstrainedSize size) {
        super(context);
        this.clippableViewDelegate = new ClippableViewDelegate();
        this.constrainedViewDelegate = new ConstrainedViewDelegate(this, size);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        constrainedViewDelegate.onMeasure(widthMeasureSpec, heightMeasureSpec, this::measureChild, super::onMeasure);
    }

    /**
     * {@inheritDoc}
     */
    @MainThread
    @Override
    public void setClipPathBorderRadius(@Dimension float borderRadius) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius);
    }
}
