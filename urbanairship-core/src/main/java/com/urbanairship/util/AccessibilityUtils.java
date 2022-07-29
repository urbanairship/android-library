/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

public final class AccessibilityUtils {

    private AccessibilityUtils() {}

    public static void setClickActionLabel(@NonNull View view, @NonNull String label) {
        ViewCompat.replaceAccessibilityAction(
            view,
            AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK,
            label,
            null
        );
    }

    public static void setClickActionLabel(@NonNull View view, @StringRes int labelRes) {
        String label = view.getResources().getString(labelRes);
        setClickActionLabel(view, label);
    }
}
