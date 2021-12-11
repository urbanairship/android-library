/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.util;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InitialMargins extends InitialSpacing {
    public InitialMargins(@NonNull ViewGroup.MarginLayoutParams lp) {
        super(lp.leftMargin, lp.topMargin, lp.rightMargin, lp.bottomMargin);
    }
}
