package com.urbanairship.messagecenter.core.util

import android.view.View
import androidx.fragment.app.Fragment

/** Sets whether the Fragment's view is important for accessibility. */
public fun Fragment.setImportantForAccessibility(important: Boolean) {
    view?.importantForAccessibility = if (important) {
        View.IMPORTANT_FOR_ACCESSIBILITY_YES
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}
