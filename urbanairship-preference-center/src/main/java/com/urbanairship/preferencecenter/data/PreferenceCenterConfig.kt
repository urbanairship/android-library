package com.urbanairship.preferencecenter.data

import androidx.annotation.RestrictTo
import com.urbanairship.remotedata.RemoteDataPayload

/**
 * Configuration for a Preference Center.
 */
data class PreferenceCenterConfig(
    val id: String
) {

    companion object {

        /**
         * Creates a `PreferenceCenterConfig` from a `RemoteDataPayload`.
         *
         * @param payload A Remote Data payload containing configuration for a Preference Center.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        internal fun fromRemoteData(payload: RemoteDataPayload): PreferenceCenterConfig {
            // TODO: add remaining fields to the data class and read them from remote data here
            val id = payload.data.opt("id").optString()

            return PreferenceCenterConfig(id = id)
        }
    }
}
