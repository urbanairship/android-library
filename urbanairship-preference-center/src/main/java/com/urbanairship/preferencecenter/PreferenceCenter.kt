package com.urbanairship.preferencecenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipComponentGroups
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.PreferenceCenterPayload
import com.urbanairship.preferencecenter.ui.PreferenceCenterActivity
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Preference Center.
 */
class PreferenceCenter @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
) : AirshipComponent(context, dataStore) {

    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

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
        internal const val DEEP_LINK_HOST = "preferences"
    }

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
        get() = privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)

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
            UALog.w("Unable to open Preference Center! FEATURE_TAGS_AND_ATTRIBUTES not enabled.")
            return
        }
        if (openListener?.onOpenPreferenceCenter(preferenceCenterId) != true) {
            UALog.v("Launching PreferenceCenterActivity with id = $preferenceCenterId")

            val intent = Intent(context, PreferenceCenterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PreferenceCenterActivity.EXTRA_ID, preferenceCenterId)

            context.startActivity(intent)
        }
    }

    /**
     * Returns the preference center config, or null if its not found.
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    suspend fun getConfig(preferenceCenterId: String): PreferenceCenterConfig? {
        val config = getJsonConfig(preferenceCenterId) ?: return null
        return try {
            PreferenceCenterConfig.parse(config.optMap())
        } catch (e: Exception) {
            UALog.w(e) { "Failed to parse preference center config" }
            null
        }
    }

    /**
     * Returns a [PendingResult] containing the configuration of the Preference Center form with the
     * given [preferenceCenterId].
     *
     * @hide
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun getConfigPendingResult(preferenceCenterId: String): PendingResult<PreferenceCenterConfig> {
        val pendingResult = PendingResult<PreferenceCenterConfig>()
        pendingResultScope.launch {
            pendingResult.result = getConfig(preferenceCenterId)
        }
        return pendingResult
    }

    /**
     * Returns the configuration of the Preference Center form with the
     * given [preferenceCenterId] as JSON.
     *
     * @hide
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested preference center config, or null if not found.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    suspend fun getJsonConfig(preferenceCenterId: String): JsonValue? {
        val payloads = remoteData.payloads(PAYLOAD_TYPE)
        for (payload in payloads) {
            val forms = payload.data.opt(KEY_PREFERENCE_FORMS).optList()
            for (form in forms) {
                val formData = form.optMap().opt(PreferenceCenterPayload.KEY_FORM).optMap()
                val id = formData.opt(PreferenceCenterConfig.KEY_ID).optString()
                if (preferenceCenterId == id) {
                    return formData.toJsonValue()
                }
            }
        }
        return null
    }

    /**
     * Returns a [PendingResult] containing the configuration of the Preference Center form with the
     * given [preferenceCenterId].
     *
     * @hide
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun getJsonConfigPendingResult(preferenceCenterId: String): PendingResult<JsonValue> {
        val pendingResult = PendingResult<JsonValue>()
        pendingResultScope.launch {
            pendingResult.result = getJsonConfig(preferenceCenterId)
        }
        return pendingResult
    }

    override fun onAirshipDeepLink(uri: Uri): Boolean {
        val paths = uri.pathSegments
        return if (DEEP_LINK_HOST == uri.encodedAuthority && paths.size == 1) {
            open(paths[0])
            true
        } else {
            false
        }
    }
}
