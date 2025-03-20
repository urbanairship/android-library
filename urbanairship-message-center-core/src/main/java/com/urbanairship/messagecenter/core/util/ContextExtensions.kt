package com.urbanairship.messagecenter.core.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

public tailrec fun Context.getActivity(): Activity? =
    (this as? Activity) ?: (this as? ContextWrapper)?.baseContext?.getActivity()
