package com.urbanairship.debug.utils

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

internal inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? {
    // The new getParcelable API was added in API 33, but came with a bug that could lead to
    // NPEs. This was fixed in API 34, but we still need to use the deprecated API for 33.
    // see: https://issuetracker.google.com/issues/240585930
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
        getParcelable(key, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelable(key) as? T
    }
}
