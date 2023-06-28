package com.urbanairship.android.layout.util

import android.app.Activity
import android.os.Build

internal inline fun <reified T> Activity.parcelableExtra(key: String): T? = when {
    Build.VERSION.SDK_INT >= 33 -> intent.getParcelableExtra(key, T::class.java)
    else -> @Suppress("DEPRECATION") intent.getParcelableExtra(key) as? T
}
