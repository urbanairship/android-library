package com.urbanairship.preferencecenter

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipComponent
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UAirship

/**
 * Airship Preference Center.
 */
class PreferenceCenter

/**
 * Full constructor (for tests).
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @VisibleForTesting internal constructor(
    context: Context,
    dataStore: PreferenceDataStore
) : AirshipComponent(context, dataStore) {

    companion object {

        /**
         * Gets the shared `PreferenceCenter` instance.
         *
         * @return an instance of `PreferenceCenter`.
         */
        @JvmStatic
        fun shared(): PreferenceCenter {
            return UAirship.shared().requireComponent(PreferenceCenter::class.java)
        }
    }
}
