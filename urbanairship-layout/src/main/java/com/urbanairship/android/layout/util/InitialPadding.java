/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InitialPadding extends InitialSpacing {
    public InitialPadding(@NonNull View view) {
        super(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), view.getPaddingBottom());
    }
}
