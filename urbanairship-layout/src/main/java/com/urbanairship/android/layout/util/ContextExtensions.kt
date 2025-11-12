package com.urbanairship.android.layout.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.urbanairship.android.layout.info.LocalizedContentDescription
import com.urbanairship.util.stringResource

internal tailrec fun Context.getActivity(): Activity? =
    (this as? Activity) ?: (this as? ContextWrapper)?.baseContext?.getActivity()


internal fun Context.resolveContentDescription(
    contentDescription: String? = null,
    localizedContentDescription: LocalizedContentDescription? = null
) : String? {
    if (contentDescription != null) { return contentDescription }

    return localizedContentDescription?.refs
        ?.firstNotNullOfOrNull { it.stringResource(this) }
        ?: localizedContentDescription?.ref?.stringResource(this)
        ?: localizedContentDescription?.fallback
}
