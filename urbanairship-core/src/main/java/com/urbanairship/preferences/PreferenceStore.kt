/* Copyright Airship and Contributors */
package com.urbanairship.preferences

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipConfigOptions
import com.urbanairship.PreferenceDataStore

/**
 * Container for Airship preference accessors.
 *
 * Currently exposes only [sync], a write-through store backed by an in-memory map with an eager
 * load at takeoff. A lazy/async accessor will be added in a follow-up.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class PreferenceStore @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) public constructor(
    public val sync: PreferenceDataStore
) {

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun tearDown() {
        sync.tearDown()
    }

    public companion object {

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun load(context: Context, configOptions: AirshipConfigOptions): PreferenceStore =
            PreferenceStore(PreferenceDataStore.loadDataStore(context, configOptions))

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        @VisibleForTesting
        public fun inMemoryStore(context: Context): PreferenceStore =
            PreferenceStore(PreferenceDataStore.inMemoryStore(context))
    }
}
