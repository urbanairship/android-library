package com.urbanairship.android.layout.util

import android.app.Activity
import android.os.Build

internal inline fun <reified T> Activity.parcelableExtra(key: String): T? = when {
    // The new getParcelableExtra API was added in API 33, but came with a bug that could lead to
    // NPEs. This was fixed in API 34, but we still need to use the deprecated API for 33.
    // see: https://issuetracker.google.com/issues/240585930
    Build.VERSION.SDK_INT > 33 -> intent.getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") intent.getParcelableExtra(key) as? T
}
