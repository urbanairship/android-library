/* Copyright Airship and Contributors */
package com.urbanairship

import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import com.urbanairship.PrivacyManager.Feature
import com.urbanairship.config.RemoteConfigObserver
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The privacy manager allow enabling/disabling features in the SDK that require user data.
 * The SDK will not make any network requests or collect data if all features our disabled, with
 * a few exceptions when going from enabled -> disabled. To have the SDK opt-out of all features on startup,
 * set the default enabled features in the AirshipConfig to [Feature.NONE], or in the
 * airshipconfig.properties file with `enabledFeatures = none`.
 *
 * Some features might offer additional opt-in settings directly on the module. For instance, enabling
 * [Feature.PUSH] will only enable push message delivery, however you still need to opt-in to
 * [com.urbanairship.push.PushManager.setUserNotificationsEnabled] before notifications
 * will be allowed.
 *
 * If any feature is enabled, the SDK will collect and send the following data:
 * - Channel ID
 * - Locale
 * - TimeZone
 * - Platform
 * - Opt in state (push and notifications)
 * - SDK version
 * - Push provider (HMS, FCM, ADM)
 * - Manufacturer (if Huawei)
 */
public class PrivacyManager @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) @JvmOverloads constructor(
    private val dataStore: PreferenceDataStore,
    private val defaultEnabledFeatures: Feature,
    private val configObserver: RemoteConfigObserver = RemoteConfigObserver(dataStore),
    resetEnabledFeatures: Boolean = false
) {

    /**
     * Privacy Manager listener.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface Listener {

        /**
         * Called when the set of enabled features changes.
         */
        public fun onEnabledFeaturesChanged()
    }

    private val lock = ReentrantLock()
    private val listeners: MutableList<Listener> = CopyOnWriteArrayList()
    private var lastUpdated = Feature.NONE

    init {
        if (resetEnabledFeatures) {
            dataStore.remove(ENABLED_FEATURES_KEY)
        }

        lastUpdated = enabledFeatures

        migrateData()

        configObserver.addConfigListener(this::notifyUpdate)
    }

    private fun getDisabledFeatures(): Feature {
        return configObserver.remoteConfig.disabledFeatures ?: Feature.NONE
    }

    private var localEnabledFeature: Feature
        get() {
            val stored = dataStore.getInt(ENABLED_FEATURES_KEY, defaultEnabledFeatures.rawValue)
            // Remove deprecated features from the enabled features, if any are set.
            return Feature(stored) and Feature.ALL
        }
        set(value) {
            dataStore.put(ENABLED_FEATURES_KEY, value.rawValue)
        }

    /** The current set of enabled features. */
    public var enabledFeatures: Feature
        get() {
            return localEnabledFeature.subtracting(getDisabledFeatures())
        }
        set(value) {
            lock.withLock {
                localEnabledFeature = value
                notifyUpdate()
            }
        }

    private fun notifyUpdate() {
        lock.withLock {
            val newFeatures = enabledFeatures
            if (lastUpdated == newFeatures) {
                return@withLock
            }

            lastUpdated = newFeatures
            for (listener in listeners) {
                listener.onEnabledFeaturesChanged()
            }
        }
    }


    /**
     * Sets the current enabled features, replacing any currently enabled features with the given set.
     *
     * Any features that were previously enabled and not passed into the current set will be disabled.
     *
     * @param features The features to set as enabled.
     */
    public fun setEnabledFeatures(vararg features: Feature) {
        enabledFeatures = Feature.combined(*features)
    }

    /**
     * Enables features, adding them to the set of currently enabled features.
     *
     * Any features that were previously enabled will remain enabled.
     *
     * @param features The features to enable.
     */
    public fun enable(vararg features: Feature) {
        enabledFeatures = enabledFeatures.combining(features.toList())
    }

    /**
     * Disables features, removing them from the set of currently enabled features.
     *
     * Any features that were previously enabled and not passed to `disable` will remain enabled.
     *
     * @param features The features to disable.
     */
    public fun disable(vararg features: Feature) {
        enabledFeatures = enabledFeatures.subtracting(features.toList())
    }

    /**
     * Checks if all of the given features are enabled.
     *
     * @param features The features to check.
     * @return `true` if all the provided features are enabled, otherwise `false`.
     */
    public fun isEnabled(vararg features: Feature): Boolean {
        return enabledFeatures.contains(features.toList())
    }

    /**
     * Checks if any of the given features are enabled.
     *
     * @param features The features to check.
     * @return `true` if any of the provided features are enabled, otherwise `false`.
     */
    public fun isAnyEnabled(vararg features: Feature): Boolean {
        val enabledFeatures = enabledFeatures

        for (feature in features) {
            if (enabledFeatures.contains(feature)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if any feature is enabled.
     *
     * @return `true` if any feature is enabled, otherwise `false`.
     */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val isAnyFeatureEnabled: Boolean
        get() {
            return isAnyFeatureEnabled(false)
        }

    /**
     * Checks if any feature is enabled.
     * @param ignoringRemoteConfig: `true` to ignore any remotely disable features, `false` to include them.
     * @return `true` if a feature is enabled, otherwise `false`.
     */
    internal fun isAnyFeatureEnabled(ignoringRemoteConfig: Boolean): Boolean {
        val config: Feature = if (ignoringRemoteConfig) {
            localEnabledFeature
        } else {
            enabledFeatures
        }

        return (config and Feature.ALL) != Feature.NONE
    }

    /**
     * Adds a listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Removes a listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    @VisibleForTesting
    internal fun migrateData() {
        if (dataStore.isSet(DATA_COLLECTION_ENABLED_KEY)) {
            if (dataStore.getBoolean(DATA_COLLECTION_ENABLED_KEY, false)) {
                this.setEnabledFeatures(Feature.ALL)
            } else {
                this.setEnabledFeatures(Feature.NONE)
            }
            dataStore.remove(DATA_COLLECTION_ENABLED_KEY)
        }

        if (dataStore.isSet(ANALYTICS_ENABLED_KEY)) {
            if (!dataStore.getBoolean(ANALYTICS_ENABLED_KEY, true)) {
                this.disable(Feature.ANALYTICS)
            }
            dataStore.remove(ANALYTICS_ENABLED_KEY)
        }

        if (dataStore.isSet(PUSH_TOKEN_REGISTRATION_ENABLED_KEY)) {
            if (!dataStore.getBoolean(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true)) {
                this.disable(Feature.PUSH)
            }
            dataStore.remove(PUSH_TOKEN_REGISTRATION_ENABLED_KEY)
        }

        if (dataStore.isSet(PUSH_ENABLED_KEY)) {
            if (!dataStore.getBoolean(PUSH_ENABLED_KEY, true)) {
                this.disable(Feature.PUSH)
            }
            dataStore.remove(PUSH_ENABLED_KEY)
        }

        if (dataStore.isSet(IAA_ENABLED_KEY)) {
            if (!dataStore.getBoolean(IAA_ENABLED_KEY, true)) {
                this.disable(Feature.IN_APP_AUTOMATION)
            }
            dataStore.remove(IAA_ENABLED_KEY)
        }
    }

    internal companion object {
        private const val ENABLED_FEATURES_KEY = "com.urbanairship.PrivacyManager.enabledFeatures"

        // legacy keys for migration
        @VisibleForTesting
        val DATA_COLLECTION_ENABLED_KEY: String = "com.urbanairship.DATA_COLLECTION_ENABLED"

        @VisibleForTesting
        val ANALYTICS_ENABLED_KEY: String = "com.urbanairship.analytics.ANALYTICS_ENABLED"

        @VisibleForTesting
        val PUSH_TOKEN_REGISTRATION_ENABLED_KEY: String =
            "com.urbanairship.push.PUSH_TOKEN_REGISTRATION_ENABLED"

        @VisibleForTesting
        val PUSH_ENABLED_KEY: String = "com.urbanairship.push.PUSH_ENABLED"

        @VisibleForTesting
        val IAA_ENABLED_KEY: String = "com.urbanairship.iam.enabled"
    }

    public class Feature @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) constructor(
        internal val rawValue: Int
    ) : JsonSerializable {

        internal fun subtracting(features: List<Feature>): Feature {
            return this and reduce(features).inv()
        }

        internal fun subtracting(feature: Feature?): Feature {
            val toSubtract = feature ?: return this
            return this and toSubtract.inv()
        }

        internal fun combining(features: List<Feature>): Feature {
            return this or reduce(features)
        }

        internal fun contains(features: List<Feature>): Boolean {
            val combined = NONE.combining(features)

            if (combined == NONE) {
                return this == NONE
            }

            return this.contains(combined)
        }

        internal fun contains(feature: Feature): Boolean {
            return this and feature == feature
        }

        private fun reduce(features: List<Feature>): Feature {
            return features.reduceOrNull { result, value -> result or value } ?: NONE
        }

        override fun toString(): String {
            val items = mutableListOf<String>()

            if (this.contains(IN_APP_AUTOMATION)) {
                items.add("In-App Automation")
            }
            if (this.contains(MESSAGE_CENTER)) {
                items.add("Message Center")
            }
            if (this.contains(PUSH)) {
                items.add("Push")
            }
            if (this.contains(ANALYTICS)) {
                items.add("Analytics")
            }
            if (this.contains(TAGS_AND_ATTRIBUTES)) {
                items.add("Tags and Attributes")
            }
            if (this.contains(CONTACTS)) {
                items.add("Contacts")
            }
            if (this.contains(FEATURE_FLAGS)) {
                items.add("Feature Flags")
            }

            return "AirshipFeature: [${items.joinToString(", ")}]"
        }

        private fun getNames(): List<String> {
            if (this == ALL) {
                return nameMap.keys.filter { it != "none" && it != "all" }
            }

            if (this == NONE) {
                return emptyList()
            }

            return nameMap
                .filter { it.value != NONE && it.value != ALL }
                .filter { this.contains(it.value) }
                .map { it.key }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(getNames())

        public companion object {
            /**
             * Enables In-App Automation (with Automation module).
             *
             * In addition to the default data collection, In-App Automation will collect:
             * - App Version (App update triggers)
             *
             * [.FEATURE_ANALYTICS] is required for event and screen view triggers.
             */
            @JvmField
            public val IN_APP_AUTOMATION: Feature = Feature(rawValue = 1)

            /**
             * Enables Message Center (with MessageCenter module).
             *
             * In addition to the default data collection, Message Center will collect:
             * - Message Center User
             * - Message Reads & Deletes
             */
            @JvmField
            public val MESSAGE_CENTER: Feature = Feature(rawValue = 1 shl 1)

            /**
             * Enables push.
             *
             * User notification still must be enabled using [com.urbanairship.push.PushManager.setUserNotificationsEnabled].
             *
             * In addition to the default data collection, push will collect:
             * - Push tokens
             */
            @JvmField
            public val PUSH: Feature = Feature(rawValue = 1 shl 2)

            /// Avoid using shl 3, it was used by CHAT

            /**
             * Enables analytics.
             *
             * In addition to the default data collection, analytics will collect:
             * - Events
             * - Associated Identifiers
             * - Registered Notification Types
             * - Time in app
             * - App Version
             * - Device model
             * - Device manufacturer
             * - OS version
             * - Carrier
             * - Connection type
             * - Framework usage
             */
            @JvmField
            public val ANALYTICS: Feature = Feature(rawValue = 1 shl 4)

            /**
             * Enables tags and attributes.
             *
             * In addition to the default data collection, tags and attributes will collect:
             * - Channel and Contact Tags
             * - Channel and Contact Attributes
             */
            @JvmField
            public val TAGS_AND_ATTRIBUTES: Feature = Feature(rawValue = 1 shl 5)

            /**
             * Enables contacts.
             *
             * In addition to the default data collection, contacts will collect:
             * - External ids (named user)
             */
            @JvmField
            public val CONTACTS: Feature = Feature(rawValue = 1 shl 6)

            /// Avoid using shl 7, it was used by LOCATION

            /**
             * Enables feature flags.
             *
             */
            @JvmField
            public val FEATURE_FLAGS: Feature = Feature(rawValue = 1 shl 8)

            /**
             * Helper flag that can be used to set enabled features to none.
             */
            @JvmField
            public val NONE: Feature = Feature(rawValue = 0)

            /**
             * Helper flag that is all features.
             */
            @JvmField
            public val ALL: Feature = IN_APP_AUTOMATION or ANALYTICS or MESSAGE_CENTER or
                PUSH or ANALYTICS or TAGS_AND_ATTRIBUTES or CONTACTS or FEATURE_FLAGS

            private val nameMap = mapOf(
                "push" to PUSH,
                "contacts" to CONTACTS,
                "message_center" to MESSAGE_CENTER,
                "analytics" to ANALYTICS,
                "tags_and_attributes" to TAGS_AND_ATTRIBUTES,
                "in_app_automation" to IN_APP_AUTOMATION,
                "feature_flags" to FEATURE_FLAGS,
                "all" to ALL,
                "none" to NONE
            )

            @JvmStatic
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
            public fun fromJson(value: JsonValue): Feature? {
                try {
                    val features = value.requireList()
                        .map { it.requireString() }
                        .map { nameMap[it.lowercase()] ?: throw IllegalArgumentException("Invalid feature $it") }

                    return NONE.combining(features)
                } catch(ex: Exception) {
                    UALog.e(ex) { "Failed to parse features" }
                    return null
                }
            }

            @JvmStatic
            @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
            public fun combined(vararg value: Feature): Feature {
                return NONE.combining(value.toList())
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Feature

            return rawValue == other.rawValue
        }

        override fun hashCode(): Int {
            return rawValue
        }

        internal infix fun and(other: Feature): Feature {
            return Feature(rawValue and other.rawValue)
        }

        internal infix fun or(other: Feature): Feature {
            return Feature(rawValue or other.rawValue)
        }

        private fun inv(): Feature {
            return Feature(rawValue.inv())
        }
    }
}
