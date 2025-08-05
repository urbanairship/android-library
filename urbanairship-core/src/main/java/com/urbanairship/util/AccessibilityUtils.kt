/* Copyright Airship and Contributors */
package com.urbanairship.util

import android.view.View
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

public object AccessibilityUtils {

    public fun setClickActionLabel(view: View, label: String) {
        ViewCompat.replaceAccessibilityAction(
            view, AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK, label, null
        )
    }

    public fun setClickActionLabel(view: View, @StringRes labelRes: Int) {
        val label = view.resources.getString(labelRes)
        setClickActionLabel(view, label)
    }
}
