package com.urbanairship.messagecenter.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.TypedValue
import androidx.annotation.AttrRes
import com.urbanairship.UALog

internal tailrec fun Context.getActivity(): Activity? =
    (this as? Activity) ?: (this as? ContextWrapper)?.baseContext?.getActivity()
