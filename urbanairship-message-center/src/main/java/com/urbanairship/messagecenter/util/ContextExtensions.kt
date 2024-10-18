package com.urbanairship.messagecenter.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal tailrec fun Context.getActivity(): Activity? =
    (this as? Activity) ?: (this as? ContextWrapper)?.baseContext?.getActivity()
