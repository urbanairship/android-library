package com.urbanairship.preferencecenter

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.Logger
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES
import com.urbanairship.UAirship
import com.urbanairship.job.JobDispatcher
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.remotedata.RemoteData

/**
 * Airship Preference Center.
 */
class PreferenceCenter @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
    private val jobDispatcher: JobDispatcher = JobDispatcher.shared(context)
) : AirshipComponent(context, dataStore) {

    companion object {

        /**
         * Gets the shared `PreferenceCenter` instance.
         *
         * @return an instance of `PreferenceCenter`.
         */
        @JvmStatic
        fun shared(): PreferenceCenter =
            UAirship.shared().requireComponent(PreferenceCenter::class.java)
    }

    /**
     * Listener to override Preference Center open behavior.
     */
    fun interface OnOpenListener {

        /**
         * Called when Preference Center should be opened.
         *
         * @param preferenceCenterId ID of the Preference Center to be opened.
         */
        fun onOpenPreferenceCenter(preferenceCenterId: String): Boolean
    }

    /**
     * Preference Center open listener.
     */
    var openListener: OnOpenListener? = null

    /**
     * Opens the Preference Center with the given [preferenceCenterId].
     *
     * @param preferenceCenterId The ID of the Preference Center.
     */
    fun open(preferenceCenterId: String) {
        if (!privacyManager.isEnabled(FEATURE_TAGS_AND_ATTRIBUTES)) {
            Logger.warn("Unable to open Preference Center! FEATURE_TAGS_AND_ATTRIBUTES not enabled.")
            return
        }

        if (openListener?.onOpenPreferenceCenter(preferenceCenterId) != true) {
            // TODO: start PreferenceCenterActivity and pass preferenceCenterId as an extra
        }
    }

    /**
     * Returns a [PendingResult] containing the configuration of the Preference Center with the
     * given [preferenceCenterId].
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    fun getConfig(preferenceCenterId: String): PendingResult<PreferenceCenterConfig> {
        return PendingResult<PreferenceCenterConfig>().apply {
            // TODO: get the actual config from remote data and return it, if present
            result = PreferenceCenterConfig(preferenceCenterId)
        }
    }
}
