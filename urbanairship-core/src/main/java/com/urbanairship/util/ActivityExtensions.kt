package com.urbanairship.util

import android.app.Activity
import android.content.Intent
import androidx.annotation.RestrictTo
import androidx.core.content.IntentCompat

/**
 * Helper for [Intent.getParcelableExtra] that uses [IntentCompat.getParcelableExtra].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T> Activity.parcelableExtra(key: String): T? =
    IntentCompat.getParcelableExtra(intent, key, T::class.java)
