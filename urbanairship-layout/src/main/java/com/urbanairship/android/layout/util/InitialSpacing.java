/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import androidx.annotation.RestrictTo;

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class InitialSpacing {
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public InitialSpacing(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int getLeft() {
        return left;
    }

    public int getTop() {
        return top;
    }

    public int getRight() {
        return right;
    }

    public int getBottom() {
        return bottom;
    }

    @Override
    public String toString() {
        return "InitialSpacing{" +
            "left=" + left +
            ", top=" + top +
            ", right=" + right +
            ", bottom=" + bottom +
            '}';
    }
}
