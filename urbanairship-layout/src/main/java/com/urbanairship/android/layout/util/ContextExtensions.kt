package com.urbanairship.android.layout.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.util.UAStringUtil

internal tailrec fun Context.getActivity(): Activity? =
    (this as? Activity) ?: (this as? ContextWrapper)?.baseContext?.getActivity()


internal fun Context.resolveContentDescription(
    contentDescription: String? = null,
    localizedContentDescription: LocalizedContentDescription? = null
) : String? {
    if (contentDescription != null) { return contentDescription }
    if (localizedContentDescription != null) {
        if (localizedContentDescription.refs != null) {
            for (ref in localizedContentDescription.refs) {
                val string = UAStringUtil.namedStringResource(this, ref)
                if (string != null) {
                    return string
                }
            }
        } else if (localizedContentDescription.ref != null) {
            val string = UAStringUtil.namedStringResource(this, localizedContentDescription.ref)
            if (string != null) {
                return string
            }
        }

        return localizedContentDescription.fallback
    }
    return null
}
