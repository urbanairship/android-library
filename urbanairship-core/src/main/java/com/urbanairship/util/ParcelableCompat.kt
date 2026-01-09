package com.urbanairship.util

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.RestrictTo
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.os.ParcelCompat

/** Compat helper for getting parcelable extras from Intents. */
internal inline fun <reified T : Parcelable> Intent.getParcelableExtraCompat(key: String): T? =
    IntentCompat.getParcelableExtra(this, key, T::class.java)

/** Compat helper for getting parcelable extras from Bundles. */
internal inline fun <reified T : Parcelable> Bundle.getParcelableCompat(key: String): T? =
    BundleCompat.getParcelable(this, key, T::class.java)

/** Compat helper for getting parcelable values from Parcels. */
internal inline fun <reified T : Parcelable> android.os.Parcel.readParcelableCompat(): T? =
    ParcelCompat.readParcelable(this, T::class.java.classLoader, T::class.java)

/**
 * Helper for [Intent.getParcelableExtra] that uses [IntentCompat.getParcelableExtra].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun <reified T> Activity.parcelableExtra(key: String): T? =
    IntentCompat.getParcelableExtra(intent, key, T::class.java)
