/* Copyright Airship and Contributors */

package com.urbanairship.audience

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.os.LocaleListCompat
import androidx.core.util.ObjectsCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.actions.FetchDeviceInfoAction
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import com.urbanairship.permission.Permission
import com.urbanairship.permission.PermissionStatus
import com.urbanairship.util.UAStringUtil
import com.urbanairship.util.VersionUtils
import java.util.Arrays
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Audience selector.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AudienceSelector private constructor(builder: Builder) : JsonSerializable {

    public val newUser: Boolean?
    public val notificationsOptIn: Boolean?
    public val locationOptIn: Boolean?
    public val requiresAnalytics: Boolean?
    public val versionPredicate: JsonPredicate?
    public val permissionsPredicate: JsonPredicate?
    public val missBehavior: MissBehavior
    public var tagSelector: DeviceTagSelector? = null

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val languageTags: List<String>

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public val testDevices: List<String>

    internal val hashSelector: AudienceHashSelector?
    internal val deviceTypes: List<String>?

    init {
        newUser = builder.newUser
        notificationsOptIn = builder.notificationsOptIn
        locationOptIn = builder.locationOptIn
        requiresAnalytics = builder.requiresAnalytics
        languageTags = builder.languageTags
        versionPredicate = builder.versionPredicate
        testDevices = builder.testDevices
        missBehavior = builder.missBehavior
        permissionsPredicate = builder.permissionsPredicate
        tagSelector = builder.tagSelector
        hashSelector = builder.hashSelector
        deviceTypes = builder.deviceTypes
    }

    override fun toJsonValue(): JsonValue {
        return JsonMap
            .newBuilder()
            .putOpt(NEW_USER_KEY, newUser)
            .putOpt(NOTIFICATION_OPT_IN_KEY, notificationsOptIn)
            .putOpt(LOCATION_OPT_IN_KEY, locationOptIn)
            .putOpt(REQUIRES_ANALYTICS_KEY, requiresAnalytics)
            .put(LOCALE_KEY, if (languageTags.isEmpty()) null else JsonValue.wrapOpt(languageTags))
            .put(
                TEST_DEVICES_KEY,
                if (testDevices.isEmpty()) null else JsonValue.wrapOpt(testDevices)
            )
            .put(FetchDeviceInfoAction.TAGS_KEY, tagSelector)
            .put(HASH_KEY, hashSelector?.toJsonValue())
            .put(APP_VERSION_KEY, versionPredicate)
            .put(MISS_BEHAVIOR_KEY, missBehavior.value)
            .put(PERMISSIONS_KEY, permissionsPredicate)
            .putOpt(DEVICE_TYPES_KEY, deviceTypes)
            .build()
            .toJsonValue()
    }

    public companion object {
        // JSON keys
        private const val NEW_USER_KEY = "new_user"
        private const val NOTIFICATION_OPT_IN_KEY = "notification_opt_in"
        private const val LOCATION_OPT_IN_KEY = "location_opt_in"
        private const val LOCALE_KEY = "locale"
        private const val APP_VERSION_KEY = "app_version"
        private const val TAGS_KEY = "tags"
        private const val TEST_DEVICES_KEY = "test_devices"
        private const val MISS_BEHAVIOR_KEY = "miss_behavior"
        private const val REQUIRES_ANALYTICS_KEY = "requires_analytics"
        private const val PERMISSIONS_KEY = "permissions"
        private const val HASH_KEY = "hash"
        private const val DEVICE_TYPES_KEY = "device_types"

        /**
         * Builder factory method.
         *
         * @return A new builder instance.
         */
        public fun newBuilder(): Builder {
            return Builder()
        }

        /**
         * Parses the json value.
         *
         * @param value The json value.
         * @return The audience condition.
         * @throws JsonException If the json is invalid.
         */
        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): AudienceSelector {
            val content = value.optMap()
            val builder: Builder = newBuilder()

            // New User
            if (content.containsKey(NEW_USER_KEY)) {
                if (!content.opt(NEW_USER_KEY).isBoolean) {
                    throw JsonException("new_user must be a boolean: " + content[NEW_USER_KEY])
                }
                builder.setNewUser(content.opt(NEW_USER_KEY).getBoolean(false))
            }

            // Push Opt-in
            if (content.containsKey(NOTIFICATION_OPT_IN_KEY)) {
                if (!content.opt(NOTIFICATION_OPT_IN_KEY).isBoolean) {
                    throw JsonException("notification_opt_in must be a boolean: " + content[NOTIFICATION_OPT_IN_KEY])
                }
                builder.setNotificationsOptIn(
                    content.opt(NOTIFICATION_OPT_IN_KEY).getBoolean(false)
                )
            }

            // Location Opt-in
            if (content.containsKey(LOCATION_OPT_IN_KEY)) {
                if (!content.opt(LOCATION_OPT_IN_KEY).isBoolean) {
                    throw JsonException("location_opt_in must be a boolean: " + content[LOCATION_OPT_IN_KEY])
                }
                builder.setLocationOptIn(content.opt(LOCATION_OPT_IN_KEY).getBoolean(false))
            }

            // Requires analytics
            if (content.containsKey(REQUIRES_ANALYTICS_KEY)) {
                if (!content.opt(REQUIRES_ANALYTICS_KEY).isBoolean) {
                    throw JsonException("requires_analytics must be a boolean: " + content[REQUIRES_ANALYTICS_KEY])
                }
                builder.setRequiresAnalytics(content.opt(REQUIRES_ANALYTICS_KEY).getBoolean(false))
            }

            // Locale
            if (content.containsKey(LOCALE_KEY)) {
                if (!content.opt(LOCALE_KEY).isJsonList) {
                    throw JsonException("locales must be an array: " + content[LOCALE_KEY])
                }
                for (`val` in content.opt(LOCALE_KEY).optList()) {
                    val tag = `val`.string ?: throw JsonException("Invalid locale: $`val`")
                    builder.addLanguageTag(tag)
                }
            }

            // App Version
            if (content.containsKey(APP_VERSION_KEY)) {
                builder.setVersionPredicate(JsonPredicate.parse(content[APP_VERSION_KEY]))
            }

            // Permissions
            if (content.containsKey(PERMISSIONS_KEY)) {
                builder.setPermissionsPredicate(JsonPredicate.parse(content[PERMISSIONS_KEY]))
            }

            // Tags
            if (content.containsKey(TAGS_KEY)) {
                builder.setTagSelector(DeviceTagSelector.fromJson(content.opt(TAGS_KEY)))
            }

            // Test devices
            if (content.containsKey(TEST_DEVICES_KEY)) {
                if (!content.opt(TEST_DEVICES_KEY).isJsonList) {
                    throw JsonException("test devices must be an array: " + content[LOCALE_KEY])
                }
                for (`val` in content.opt(TEST_DEVICES_KEY).optList()) {
                    if (!`val`.isString) {
                        throw JsonException("Invalid test device: $`val`")
                    }
                    builder.addTestDevice(`val`.string!!)
                }
            }

            // Miss Behavior
            if (content.containsKey(MISS_BEHAVIOR_KEY)) {
                if (!content.opt(MISS_BEHAVIOR_KEY).isString) {
                    throw JsonException("miss_behavior must be a string: " + content[MISS_BEHAVIOR_KEY])
                }

                val behavior = MissBehavior.parse(content.opt(MISS_BEHAVIOR_KEY).optString())
                if (behavior != null) {
                    builder.setMissBehavior(behavior)
                } else {
                    throw JsonException("Invalid miss behavior: " + content.opt(MISS_BEHAVIOR_KEY))
                }
            }

            // Audience Selector
            if (content.containsKey(HASH_KEY)) {
                if (!content.opt(HASH_KEY).isJsonMap) {
                    throw JsonException("hash must be a json map: " + content[HASH_KEY])
                }

                val hashSelector = AudienceHashSelector.fromJson(content.opt(HASH_KEY).optMap())
                if (hashSelector == null) {
                    throw JsonException("failed to parse audience hash from: " + content[HASH_KEY])
                } else {
                    builder.setAudienceHashSelector(hashSelector)
                }
            }

            // Device types
            if (content.containsKey(DEVICE_TYPES_KEY)) {
                if (!content.opt(DEVICE_TYPES_KEY).isJsonList) {
                    throw JsonException("device types must be a json list: " + content[DEVICE_TYPES_KEY])
                }
                builder.deviceTypes = content
                    .opt(DEVICE_TYPES_KEY)
                    .optList()
                    .map { it.requireString() }
            }

            return builder.build()
        }
    }

    public enum class MissBehavior(public val value: String) {
        /**
         * Cancel the message's schedule when the audience check fails.
         */
        CANCEL("cancel"),

        /**
         * Skip the message's schedule when the audience check fails.
         */
        SKIP("skip"),

        /**
         * Skip and penalize the message's schedule when the audience check fails.
         */
        PENALIZE("penalize");

        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public companion object {
            public fun parse(input: String): MissBehavior? {
                return MissBehavior.values().firstOrNull { it.value == input }
            }
        }
    }

    override fun toString(): String {
        return "AudienceSelector{" +
                "newUser=" + newUser +
                ", notificationsOptIn=" + notificationsOptIn +
                ", locationOptIn=" + locationOptIn +
                ", requiresAnalytics=" + requiresAnalytics +
                ", languageTags=" + languageTags +
                ", testDevices=" + testDevices +
                ", tagSelector=" + tagSelector +
                ", audienceHash=" + hashSelector +
                ", versionPredicate=" + versionPredicate +
                ", permissionsPredicate=" + permissionsPredicate +
                ", missBehavior='" + missBehavior + '\'' +
            '}'
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val audience: AudienceSelector = other as AudienceSelector

        return (ObjectsCompat.equals(newUser, audience.newUser) && ObjectsCompat.equals(
            notificationsOptIn,
            audience.notificationsOptIn
        ) && ObjectsCompat.equals(locationOptIn, audience.locationOptIn) && ObjectsCompat.equals(
            requiresAnalytics,
            audience.requiresAnalytics
        ) && ObjectsCompat.equals(languageTags, audience.languageTags) && ObjectsCompat.equals(
            testDevices,
            audience.testDevices
        ) && ObjectsCompat.equals(tagSelector, audience.tagSelector) && ObjectsCompat.equals(
            versionPredicate,
            audience.versionPredicate
        ) && ObjectsCompat.equals(
            permissionsPredicate,
            audience.permissionsPredicate
        ) && ObjectsCompat.equals(missBehavior, audience.missBehavior))
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            newUser,
            notificationsOptIn,
            locationOptIn,
            requiresAnalytics,
            languageTags,
            testDevices,
            tagSelector,
            versionPredicate,
            permissionsPredicate,
            missBehavior
        )
    }

    /**
     * AudienceSelector builder.
     */
    public class Builder internal constructor() {

        internal var newUser: Boolean? = null
        internal var notificationsOptIn: Boolean? = null
        internal var locationOptIn: Boolean? = null
        internal var requiresAnalytics: Boolean? = null
        internal val languageTags: MutableList<String> = ArrayList()
        internal val testDevices: MutableList<String> = ArrayList()
        internal var missBehavior = MissBehavior.PENALIZE
        internal var versionPredicate: JsonPredicate? = null
        internal var permissionsPredicate: JsonPredicate? = null
        internal var tagSelector: DeviceTagSelector? = null
        internal var hashSelector: AudienceHashSelector? = null
        internal var deviceTypes: List<String>? = null

        /**
         * Sets the new user audience condition for scheduling the in-app message.
         *
         * @param newUser `true` if only new users should schedule the in-app message, otherwise `false`.
         * @return The builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun setNewUser(newUser: Boolean): Builder {
            this.newUser = newUser
            return this
        }

        /**
         * Adds a test device.
         *
         * @param hash The hashed channel.
         * @return THe builder.
         * @hide
         */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun addTestDevice(hash: String): Builder {
            testDevices.add(hash)
            return this
        }

        internal fun setAudienceHashSelector(selector: AudienceHashSelector): Builder {
            this.hashSelector = selector
            return this
        }

        /**
         * Sets the location opt-in audience condition for the in-app message.
         *
         * @param optIn `true` if location must be opted in, otherwise `false`.
         * @return The builder.
         * @hide
         */
        public fun setLocationOptIn(optIn: Boolean): Builder {
            locationOptIn = optIn
            return this
        }

        /**
         * Sets the require analytics audience condition for the in-app message.
         *
         * @param requiresAnalytics `true` if analytics must be enabled, otherwise `false`.
         * @return The builder.
         * @hide
         */
        public fun setRequiresAnalytics(requiresAnalytics: Boolean): Builder {
            this.requiresAnalytics = requiresAnalytics
            return this
        }

        /**
         * Sets the notification opt-in audience condition for the in-app message.
         *
         * @param optIn `true` if notifications must be opted in, otherwise `false`.
         * @return The builder.
         * @hide
         */
        public fun setNotificationsOptIn(optIn: Boolean): Builder {
            notificationsOptIn = optIn
            return this
        }

        /**
         * Adds a BCP 47 location tag. Only the language and country code are used
         * to determine the audience.
         *
         * @param languageTag A BCP 47 language tag.
         * @return The builder.
         */
        public fun addLanguageTag(languageTag: String): Builder {
            languageTags.add(languageTag)
            return this
        }

        /**
         * Value predicate to be used to match the app's version int.
         *
         * @param predicate Json predicate to match the version object.
         * @return The builder.
         */
        public fun setVersionPredicate(predicate: JsonPredicate?): Builder {
            versionPredicate = predicate
            return this
        }

        /**
         * JSON predicate to be used to match the app's permissions map.
         *
         * @param predicate Json predicate to match the permissions map.
         * @return The builder.
         */
        public fun setPermissionsPredicate(predicate: JsonPredicate): Builder {
            permissionsPredicate = predicate
            return this
        }

        /**
         * Value matcher to be used to match the app's version int.
         *
         * @param valueMatcher Value matcher to be applied to the app's version int.
         * @return The builder.
         */
        public fun setVersionMatcher(valueMatcher: ValueMatcher?): Builder {
            return setVersionPredicate(
                if (valueMatcher == null) null else VersionUtils.createVersionPredicate(
                    valueMatcher
                )
            )
        }

        /**
         * Sets the tag selector. Tag selector will only be applied to channel tags set through
         * the SDK.
         *
         * @param tagSelector The tag selector.
         * @return The builder.
         */
        public fun setTagSelector(tagSelector: DeviceTagSelector?): Builder {
            this.tagSelector = tagSelector
            return this
        }

        /**
         * Sets the audience miss behavior for the in-app message.
         *
         * @param missBehavior The audience miss behavior.
         * @return The builder.
         * @hide
         */
        public fun setMissBehavior(missBehavior: MissBehavior): Builder {
            this.missBehavior = missBehavior
            return this
        }

        /**
         * Sets the device types for the in-app message.
         *
         * @param types The device types.
         * @return The builder.
         * @hide
         */
        public fun setDeviceTypes(types: List<String>?): Builder {
            this.deviceTypes = types
            return this
        }

        /**
         * Builds the in-app message audience.
         *
         * @return The audience.
         */
        public fun build(): AudienceSelector {
            return AudienceSelector(this)
        }
    }

    /**
     * Evaluation
     */

    public fun evaluateAsPendingResult(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider,
        contactId: String? = null
    ): PendingResult<Boolean> {

        val scope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
        val result = PendingResult<Boolean>()
        scope.launch {
            result.result = evaluate(context, newEvaluationDate, infoProvider, contactId)
        }

        return result
    }

    public suspend fun evaluate(
        context: Context,
        newEvaluationDate: Long,
        infoProvider: DeviceInfoProvider,
        contactId: String? = null
    ): Boolean {

        if (!checkDeviceType(infoProvider)) { return false }
        if (!checkTestDevice(infoProvider)) { return false }
        if (!checkNotificationOptInStatus(infoProvider)) { return false }
        if (!checkLocale(context, infoProvider)) { return false }
        if (!checkTags(infoProvider)) { return false }
        if (!checkAnalytics(infoProvider)) { return false }

        val permissions = infoProvider.getPermissionStatuses()
        if (!checkPermissions(permissions)) { return false }
        if (!checkLocationOptInStatus(permissions)) { return false }
        if (!checkVersion(infoProvider)) { return false }
        if (!checkNewUser(infoProvider, newEvaluationDate)) { return false }
        if (!checkHash(infoProvider, contactId)) { return false }

        return true
    }

    private fun checkDeviceType(infoProvider: DeviceInfoProvider): Boolean {
        return deviceTypes?.contains(infoProvider.platform) ?: true
    }

    private fun checkTestDevice(infoProvider: DeviceInfoProvider): Boolean {
        if (testDevices.isEmpty()) {
            return true
        }

        var digest = UAStringUtil.sha256Digest(infoProvider.channelId)
        if (digest == null || digest.size < 16) {
            return false
        }

        digest = Arrays.copyOf(digest, 16)
        for (testDevice in testDevices) {
            val decoded = UAStringUtil.base64Decode(testDevice)
            if (Arrays.equals(digest, decoded)) {
                return true
            }
        }
        return false
    }

    private fun checkNotificationOptInStatus(dataProvider: DeviceInfoProvider): Boolean {
        val required = notificationsOptIn ?: return true
        return required == dataProvider.isNotificationsOptedIn
    }

    private fun checkLocale(context: Context, dataProvider: DeviceInfoProvider): Boolean {
        if (languageTags.isEmpty()) {
            return true
        }

        val locale = dataProvider.getUserLocals(context).getFirstMatch(languageTags.toTypedArray())
            ?: return false

        // getFirstMatch will return the default language if none of the specified locales are found,
        // so we still have to verify the locale exists in the audience conditions

        // Sanitize language tags in case any happen to be malformed
        val languageTags: Set<String> = sanitizedLanguageTags(languageTags)
        try {
            val joinedTags = UAStringUtil.join(languageTags, ",")
            val audienceLocales = LocaleListCompat.forLanguageTags(joinedTags)
            for (i in 0 until audienceLocales.size()) {
                val audienceLocale = audienceLocales[i]
                if (locale.language != audienceLocale!!.language) {
                    continue
                }
                if (!UAStringUtil.isEmpty(audienceLocale.country) && audienceLocale.country != locale.country) {
                    continue
                }
                return true
            }
        } catch (e: Exception) {
            UALog.e("Unable to construct locale list: ", e)
        }
        return false
    }

    private fun checkTags(infoProvider: DeviceInfoProvider): Boolean {
        val selector = tagSelector ?: return true

        if (!infoProvider.isFeatureEnabled(PrivacyManager.FEATURE_TAGS_AND_ATTRIBUTES)) {
            return false
        }

        return selector.apply(infoProvider.channelTags)
    }

    private fun checkAnalytics(infoProvider: DeviceInfoProvider): Boolean {
        val required = requiresAnalytics ?: return true
        if (!required) { return true }

        return infoProvider.isFeatureEnabled(PrivacyManager.FEATURE_ANALYTICS)
    }

    private fun checkPermissions(permissions: Map<Permission, PermissionStatus>): Boolean {
        val required = permissionsPredicate ?: return true
        val converted = permissions.map { it.key.value to it.value.value }.toMap()

        return required.apply(JsonValue.wrap(converted))
    }

    private fun checkLocationOptInStatus(permissions: Map<Permission, PermissionStatus>): Boolean {
        val required = locationOptIn ?: return true
        val locationPermission = permissions[Permission.LOCATION] ?: return false
        val current = PermissionStatus.GRANTED == locationPermission

        return current == required
    }

    private fun checkVersion(infoProvider: DeviceInfoProvider): Boolean {
        val required = versionPredicate ?: return true
        val version = VersionUtils.createVersionObject(infoProvider.appVersion)
        return required.apply(version)
    }

    private fun checkNewUser(infoProvider: DeviceInfoProvider, cutOffDate: Long): Boolean {
        val required = newUser ?: return true

        return required == (infoProvider.appVersion >= cutOffDate)
    }

    private suspend fun checkHash(infoProvider: DeviceInfoProvider, contactId: String?): Boolean {
        val selector = hashSelector ?: return true

        val channelId = infoProvider.channelId ?: return false
        val useContactId = contactId ?: infoProvider.getStableContactId()

        return selector.evaluate(channelId, useContactId)
    }

    private fun sanitizedLanguageTags(languageTags: List<String>): Set<String> {
        return languageTags
            .mapNotNull { language ->
                if (language.isEmpty()) { return@mapNotNull null }
                if (language.endsWith("_") || language.endsWith("-")) {
                    return@mapNotNull language.dropLast(1)
                }
                language
            }
            .toSet()
    }
}
