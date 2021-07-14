package com.urbanairship.preferencecenter

import android.content.Context
import android.os.Looper
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipLoopers
import com.urbanairship.Logger
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES
import com.urbanairship.UAirship
import com.urbanairship.job.JobDispatcher
import com.urbanairship.json.JsonException
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.reactive.Observable
import com.urbanairship.reactive.Schedulers
import com.urbanairship.reactive.Subscriber
import com.urbanairship.remotedata.RemoteData

/**
 * Airship Preference Center.
 */
class PreferenceCenter @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
    backgroundLooper: Looper = AirshipLoopers.getBackgroundLooper(),
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

        internal const val PAYLOAD_TYPE = "preference_forms"
        internal const val KEY_PREFERENCE_FORMS = "preference_forms"
    }

    private val backgroundScheduler = Schedulers.looper(backgroundLooper)

    /**
     * Listener to override Preference Center open behavior.
     */
    fun interface OnOpenListener {

        /**
         * Called when Preference Center should be opened.
         *
         * @param preferenceCenterId ID of the Preference Center to be opened.
         * @return `true` if the preference center was shown, otherwise `false` to trigger the default behavior.
         */
        fun onOpenPreferenceCenter(preferenceCenterId: String): Boolean
    }

    /**
     * Preference Center open listener.
     */
    var openListener: OnOpenListener? = null

    private val isFeatureEnabled: Boolean
        get() = privacyManager.isEnabled(FEATURE_TAGS_AND_ATTRIBUTES)

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    override fun getComponentGroup(): Int = AirshipComponentGroups.PREFERENCE_CENTER

    /**
     * Opens the Preference Center with the given [preferenceCenterId].
     *
     * @param preferenceCenterId The ID of the Preference Center.
     */
    fun open(preferenceCenterId: String) {
        if (!isFeatureEnabled) {
            Logger.warn("Unable to open Preference Center! FEATURE_TAGS_AND_ATTRIBUTES not enabled.")
            return
        }

        val result = getConfig(preferenceCenterId)
        result.addResultCallback { form ->
            form?.let {
                if (openListener?.onOpenPreferenceCenter(preferenceCenterId) != true) {
                    // TODO: start PreferenceCenterActivity and pass preferenceCenterId as an extra
                    Logger.verbose("TODO: Launch PreferenceCenterActivity with id = $preferenceCenterId")
                }
            } ?: Logger.error("Unable to open Preference Center! No configuration was found for id: '$preferenceCenterId'.")
        }
    }

    /**
     * Returns a [PendingResult] containing the configuration of the Preference Center form with the
     * given [preferenceCenterId].
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceForm].
     */
    fun getConfig(preferenceCenterId: String): PendingResult<PreferenceCenterConfig> {
        val pendingResult = PendingResult<PreferenceCenterConfig>()

        remoteData.payloadsForType(PAYLOAD_TYPE)
                .flatMap { payload ->
                    val forms = payload.data.opt(KEY_PREFERENCE_FORMS).optList()
                    Logger.verbose("Found ${forms.size()} preference forms in RemoteData")
                    val preferenceForms = try {
                        // Parse the payloads and return the list as a map of ID to PreferenceForms.
                        forms.map { PreferenceCenterConfig.parse(it.optMap()) }.associateBy { it.id }
                    } catch (e: JsonException) {
                        return@flatMap Observable.error(e)
                    }
                    Observable.just(preferenceForms)
                }
                .subscribeOn(backgroundScheduler)
                .observeOn(backgroundScheduler)
                .subscribe(object : Subscriber<Map<String, PreferenceCenterConfig>>() {
                    override fun onNext(value: Map<String, PreferenceCenterConfig>) {
                        pendingResult.result = value[preferenceCenterId]
                    }

                    override fun onError(e: Exception) {
                        Logger.error(e, "Failed to get preference center config for ID: $preferenceCenterId")
                        pendingResult.result = null
                    }
                })

        return pendingResult
    }
}
