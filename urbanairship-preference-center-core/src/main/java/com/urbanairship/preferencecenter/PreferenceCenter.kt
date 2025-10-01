package com.urbanairship.preferencecenter

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.AirshipComponent
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.inputvalidation.AirshipInputValidation
import com.urbanairship.json.JsonValue
import com.urbanairship.preferencecenter.data.PreferenceCenterConfig
import com.urbanairship.preferencecenter.data.PreferenceCenterPayload
import com.urbanairship.remotedata.RemoteData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Airship Preference Center.
 */
public class PreferenceCenter internal constructor(
    context: Context,
    dataStore: PreferenceDataStore,
    private val privacyManager: PrivacyManager,
    private val remoteData: RemoteData,
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val inputValidator: AirshipInputValidation.Validator
) : AirshipComponent(context, dataStore) {

    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    public companion object {
        /**
         * Gets the shared `PreferenceCenter` instance.
         *
         * @return an instance of `PreferenceCenter`.
         */
        @JvmStatic
        public fun shared(): PreferenceCenter =
            Airship.shared().requireComponent(PreferenceCenter::class.java)

        /**
         * Parses the Preference Center ID from the given [Intent].
         *
         * @param intent The intent to parse.
         * @return The ID of the Preference Center, or null if not found.
         */
        public fun parsePreferenceCenterId(intent: Intent?): String? {
            return intent?.getStringExtra(EXTRA_PREFERENCE_CENTER_ID)
        }

        internal const val PAYLOAD_TYPE = "preference_forms"
        internal const val KEY_PREFERENCE_FORMS = "preference_forms"
        internal const val DEEP_LINK_HOST = "preferences"

        /**
         * Intent action to view the preference center.
         */
        public const val VIEW_PREFERENCE_CENTER_INTENT_ACTION: String = "com.urbanairship.VIEW_PREFERENCE_CENTERX"

        /**
         * Required `String` extra specifying the ID of the Preference Center to be displayed.
         */
        public const val EXTRA_PREFERENCE_CENTER_ID: String = "com.urbanairship.preferencecenter.PREF_CENTER_ID"
    }

    /**
     * Listener to override Preference Center open behavior.
     */
    public fun interface OnOpenListener {

        /**
         * Called when Preference Center should be opened.
         *
         * @param preferenceCenterId ID of the Preference Center to be opened.
         * @return `true` if the preference center was shown, otherwise `false` to trigger the default behavior.
         */
        public fun onOpenPreferenceCenter(preferenceCenterId: String): Boolean
    }

    /**
     * Preference Center open listener.
     */
    public var openListener: OnOpenListener? = null

    private val isFeatureEnabled: Boolean
        get() = privacyManager.isEnabled(PrivacyManager.Feature.TAGS_AND_ATTRIBUTES)


    /**
     * Opens the Preference Center with the given [preferenceCenterId].
     *
     * @param preferenceCenterId The ID of the Preference Center.
     */
    public fun open(preferenceCenterId: String) {
        if (!isFeatureEnabled) {
            UALog.w("Unable to open Preference Center! FEATURE_TAGS_AND_ATTRIBUTES not enabled.")
            return
        }

        if (openListener?.onOpenPreferenceCenter(preferenceCenterId) == true) {
            return
        }

        UALog.v("Launching PreferenceCenterActivity with id = $preferenceCenterId")

        val intent = Intent(VIEW_PREFERENCE_CENTER_INTENT_ACTION)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PREFERENCE_CENTER_ID, preferenceCenterId)

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }

        var missingXmlModule = false
        var missingComposeModule = false

        try {
            val clazz = Class.forName("com.urbanairship.preferencecenter.compose.ui.PreferenceCenterActivity")
            intent.setClass(context, clazz)
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            missingComposeModule = true
        }

        // Fallback to the message center activity, if available
        try {
            val clazz = Class.forName("com.urbanairship.preferencecenter.ui.PreferenceCenterActivity")
            intent.setClass(context, clazz)
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            missingXmlModule = true
        }

        if (missingXmlModule || missingComposeModule) {
            UALog.w { "Unable to start PreferenceCenterActivity, the preference-center or preference-center-compose module is required." }
        }
    }

    /**
     * Returns the preference center config, or null if its not found.
     *
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    public suspend fun getConfig(preferenceCenterId: String): PreferenceCenterConfig? {
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
     * @param preferenceCenterId The ID of the Preference Center.
     * @return The requested [PreferenceCenterConfig].
     */
    public fun getConfigPendingResult(preferenceCenterId: String): PendingResult<PreferenceCenterConfig> {
        val pendingResult = PendingResult<PreferenceCenterConfig>()
        pendingResultScope.launch {
            pendingResult.setResult(getConfig(preferenceCenterId))
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
    public suspend fun getJsonConfig(preferenceCenterId: String): JsonValue? {
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
    public fun getJsonConfigPendingResult(preferenceCenterId: String): PendingResult<JsonValue> {
        val pendingResult = PendingResult<JsonValue>()
        pendingResultScope.launch {
            pendingResult.setResult(getJsonConfig(preferenceCenterId))
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
