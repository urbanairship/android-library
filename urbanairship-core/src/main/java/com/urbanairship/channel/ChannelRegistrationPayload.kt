/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireMap
import com.urbanairship.push.PushProvider

/**
 * Model object encapsulating the data relevant to a creation or updates processed by ChannelApiClient.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ChannelRegistrationPayload private constructor(builder: Builder) : JsonSerializable {

    public enum class DeviceType(
        private val value: String
    ) : JsonSerializable {

        AMAZON("amazon"),
        ANDROID("android");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)

        internal companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): DeviceType {
                val stringValue = value.requireString()
                return entries.firstOrNull { it.value == stringValue }
                    ?: throw JsonException("invalid device type $stringValue")
            }
        }
    }

    public val optIn: Boolean
    public val backgroundEnabled: Boolean
    public val deviceType: DeviceType?
    public val pushAddress: String?
    public val setTags: Boolean
    public val tags: Set<String>?
    public val tagChanges: JsonMap?
    public val userId: String?
    public val timezone: String?
    public val language: String?
    public val country: String?
    public val locationSettings: Boolean?
    public val appVersion: String?
    public val sdkVersion: String?
    public val deviceModel: String?
    public val apiVersion: Int?
    public val deliveryType: PushProvider.DeliveryType?
    public val contactId: String?
    public val isActive: Boolean
    public val permissions: Map<String, String>?

    /**
     * Builds the ChannelRegistrationPayload
     */
    public class Builder {
        internal var optIn: Boolean = false
        public var backgroundEnabled: Boolean = false
            private set
        public var deviceType: DeviceType? = null
            private set
        public var pushAddress: String? = null
            private set
        public var setTags: Boolean = false
            private set
        public var tags: Set<String>? = null
            private set
        public var tagChanges: JsonMap? = null
            private set
        public var userId: String? = null
            private set
        public var timezone: String? = null
            private set
        public var language: String? = null
            private set
        public var country: String? = null
            private set
        public var locationSettings: Boolean? = null
            private set
        public var appVersion: String? = null
            private set
        public var sdkVersion: String? = null
            private set
        public var deviceModel: String? = null
            private set
        public var apiVersion: Int? = null
            private set
        public var carrier: String? = null
            private set
        public var accengageDeviceId: String? = null
            private set
        public var deliveryType: PushProvider.DeliveryType? = null
            private set
        public var contactId: String? = null
            private set
        public var isActive: Boolean = false
            private set
        public var permissions: Map<String, String>? = null
            private set

        /**
         * Default ChannelRegistrationPayload.Builder constructor
         */
        public constructor()

        /**
         * ChannelRegistrationPayload.Builder constructor that draws from an existing payload.
         *
         * @param payload The payload.
         */
        public constructor(payload: ChannelRegistrationPayload) {
            this.optIn = payload.optIn
            this.backgroundEnabled = payload.backgroundEnabled
            this.deviceType = payload.deviceType
            this.pushAddress = payload.pushAddress
            this.setTags = payload.setTags
            this.tags = payload.tags
            this.tagChanges = payload.tagChanges
            this.userId = payload.userId
            this.timezone = payload.timezone
            this.language = payload.language
            this.country = payload.country
            this.locationSettings = payload.locationSettings
            this.appVersion = payload.appVersion
            this.sdkVersion = payload.sdkVersion
            this.deviceModel = payload.deviceModel
            this.apiVersion = payload.apiVersion
            this.deliveryType = payload.deliveryType
            this.contactId = payload.contactId
            this.isActive = payload.isActive
            this.permissions = payload.permissions
        }

        /**
         * Set the optIn value
         *
         * @param optIn A boolean value indicating if optIn is true or false.
         * @return The builder with optIn value set
         */
        public fun setOptIn(optIn: Boolean): Builder {
            return this.also { it.optIn = optIn }
        }

        /**
         * Set the background enabled value.
         *
         * @param enabled enabled A boolean value indicating whether background push is enabled.
         * @return The builder with the background push enabled value set.
         */
        public fun setBackgroundEnabled(enabled: Boolean): Builder {
            return this.also { it.backgroundEnabled = enabled }
        }

        /**
         * Set the device type
         *
         * @param deviceType Device type enum value
         * @return The builder with device type set
         */
        public fun setDeviceType(deviceType: DeviceType?): Builder {
            return this.also { it.deviceType = deviceType }
        }

        /**
         * Set the contact Id.
         *
         * @param contactId The contact Id.
         * @return The builder instance.
         */
        public fun setContactId(contactId: String?): Builder {
            return this.also { it.contactId = contactId }
        }

        /**
         * Set the device timezone
         *
         * @param timezone A string value of the timezone ID
         * @return The builder with timezone ID set
         */
        public fun setTimezone(timezone: String?): Builder {
            return this.also { it.timezone = timezone }
        }

        /**
         * Set the device language
         *
         * @param language A string value of the language ID
         * @return The builder with language ID set
         */
        public fun setLanguage(language: String?): Builder {
            return this.also { it.language = language }
        }

        /**
         * Set the device country
         *
         * @param country A string value of the country ID
         * @return The builder with country ID set
         */
        public fun setCountry(country: String?): Builder {
            return this.also { it.country = country }
        }

        /**
         * Set the push address
         *
         * @param registrationId A string value
         * @return The builder with push address set
         */
        public fun setPushAddress(registrationId: String?): Builder {
            return this.also { it.pushAddress = registrationId }
        }

        /**
         * Set tags
         *
         * @param channelTagRegistrationEnabled A boolean value indicating whether tags are enabled on the device.
         * @param tags A set of tags
         * @return The builder with channelTagRegistrationEnabled and tags set
         */
        public fun setTags(channelTagRegistrationEnabled: Boolean, tags: Set<String>?): Builder {
            return this.also {
                it.setTags = channelTagRegistrationEnabled
                it.tags = tags
            }
        }

        /**
         * Set tag changes
         *
         * @param tagChanges A map containing a set of add tags and a set of remove tags
         * @return The builder with channelTagRegistrationEnabled and tag changes set
         */
        public fun setTagChanges(tagChanges: JsonMap?): Builder {
            return this.also { it.tagChanges = tagChanges }
        }

        /**
         * Set the userId
         *
         * @param userId A string value
         * @return The builder with userId value set
         */
        public fun setUserId(userId: String?): Builder {
            return this.also {
                it.userId = if (userId?.isEmpty() == true) null else userId
            }
        }

        /**
         * Set the location settings
         *
         * @param locationSettings The location settings
         * @return The builder.
         */
        public fun setLocationSettings(locationSettings: Boolean?): Builder {
            return this.also { it.locationSettings = locationSettings }
        }

        /**
         * Set the app version
         *
         * @param appVersion The app version
         * @return The builder.
         */
        public fun setAppVersion(appVersion: String?): Builder {
            return this.also { it.appVersion = appVersion }
        }

        /**
         * Set the SDK version
         *
         * @param sdkVersion The SDK version
         * @return The builder.
         */
        public fun setSdkVersion(sdkVersion: String?): Builder {
            return this.also { it.sdkVersion = sdkVersion }
        }

        /**
         * Set the device model
         *
         * @param deviceModel The device model
         * @return The builder.
         */
        public fun setDeviceModel(deviceModel: String?): Builder {
            return this.also { it.deviceModel = deviceModel }
        }

        /**
         * Set the API version
         *
         * @param apiVersion The API version.
         * @return The builder.
         */
        public fun setApiVersion(apiVersion: Int?): Builder {
            return this.also { it.apiVersion = apiVersion }
        }

        /**
         * Set isActive flag.
         *
         * @param isActive `true` if active, otherwise `false`.
         * @return The builder.
         */
        public fun setIsActive(isActive: Boolean): Builder {
            return this.also { it.isActive = isActive }
        }

        /**
         * Set permissions.
         *
         * @param permissions Permission name with status.
         * @return The builder.
         */
        public fun setPermissions(permissions: Map<String, String>?): Builder {
            return this.also { it.permissions = permissions }
        }

        /**
         * Set the delivery type.
         *
         * @param deliveryType The delivery type.
         * @return The builder.
         */
        public fun setDeliveryType(deliveryType: PushProvider.DeliveryType?): Builder {
            return this.also { it.deliveryType = deliveryType }
        }

        public fun build(): ChannelRegistrationPayload {
            return ChannelRegistrationPayload(this)
        }
    }

    init {
        this.optIn = builder.optIn
        this.backgroundEnabled = builder.backgroundEnabled
        this.deviceType = builder.deviceType
        this.pushAddress = builder.pushAddress
        this.setTags = builder.setTags
        this.tags = if (builder.setTags) builder.tags else null
        this.tagChanges = builder.tagChanges
        this.userId = builder.userId
        this.timezone = builder.timezone
        this.language = builder.language
        this.country = builder.country
        this.locationSettings = builder.locationSettings
        this.appVersion = builder.appVersion
        this.sdkVersion = builder.sdkVersion
        this.deviceModel = builder.deviceModel
        this.apiVersion = builder.apiVersion
        this.deliveryType = builder.deliveryType
        this.contactId = builder.contactId
        this.isActive = builder.isActive
        this.permissions = builder.permissions
    }

    public fun minimizedPayload(last: ChannelRegistrationPayload?): ChannelRegistrationPayload {
        if (last == null) {
            return this
        }

        val builder = Builder(this)
            .setUserId(null)

        if (last.setTags && setTags) {
            last.tags?.let { tagsUpdate ->
                if (tagsUpdate == tags) {
                    builder.setTags(false, null)
                } else {
                    try {
                        builder.setTagChanges(getTagChanges(last.tags))
                    } catch (e: JsonException) {
                        UALog.d(e, "ChannelRegistrationPayload - Failed to wrap tag changes to JsonMap")
                    }
                }
            }
        }

        // Only remove attributes if contact id is null or is the same as the last payload
        if (contactId == null || contactId == last.contactId) {
            if (last.country == country) {
                builder.setCountry(null)
            }

            if (last.language ==  language) {
                builder.setLanguage(null)
            }

            if (last.timezone == timezone) {
                builder.setTimezone(null)
            }

            if (last.locationSettings == locationSettings) {
                builder.setLocationSettings(null)
            }

            if (last.appVersion == appVersion) {
                builder.setAppVersion(null)
            }

            if (last.sdkVersion == sdkVersion) {
                builder.setSdkVersion(null)
            }

            if (last.deviceModel == deviceModel) {
                builder.setDeviceModel(null)
            }

            if (last.apiVersion == apiVersion) {
                builder.setApiVersion(null)
            }
        }

        if (permissions !== last.permissions) {
            builder.setPermissions(permissions)
        }

        return builder.build()
    }

    @Throws(JsonException::class)
    private fun getTagChanges(lastTags: Set<String>): JsonMap {
        val add = tags?.filter { !lastTags.contains(it) }
        val remove = lastTags.filter { tags?.contains(it) != true }

        val builder = JsonMap.newBuilder()
        if (add?.isNotEmpty() == true) {
            builder.put(TAG_CHANGES_ADD_KEY, JsonValue.wrap(add))
        }
        if (remove.isNotEmpty()) {
            builder.put(TAG_CHANGES_REMOVE_KEY, JsonValue.wrap(remove))
        }

        return builder.build()
    }

    override fun toJsonValue(): JsonValue {
        // Channel Payload

        val channel = JsonMap.newBuilder()
            .put(DEVICE_TYPE_KEY, deviceType)
            .put(SET_TAGS_KEY, setTags)
            .put(OPT_IN_KEY, optIn)
            .put(PUSH_ADDRESS_KEY, pushAddress)
            .put(BACKGROUND_ENABLED_KEY, backgroundEnabled)
            .put(TIMEZONE_KEY, timezone)
            .put(LANGUAGE_KEY, language)
            .put(COUNTRY_KEY, country)
            .put(APP_VERSION_KEY, appVersion)
            .put(SDK_VERSION_KEY, sdkVersion)
            .put(DEVICE_MODEL_KEY, deviceModel)
            .put(CONTACT_ID_KEY, contactId)
            .put(IS_ACTIVE, isActive)

        if (deviceType == DeviceType.ANDROID && deliveryType != null) {
            channel.put(ANDROID_EXTRAS_KEY, jsonMapOf(ANDROID_DELIVERY_TYPE to deliveryType))
        }

        locationSettings?.let { channel.put(LOCATION_SETTINGS_KEY, it) }
        apiVersion?.let { channel.put(API_VERSION_KEY, it) }

        // If setTags is TRUE, then include the tags
        if (setTags && tags != null) {
            channel.put(TAGS_KEY, JsonValue.wrapOpt(tags).list)
        }

        if (setTags && tagChanges != null) {
            channel.put(TAG_CHANGES_KEY, JsonValue.wrapOpt(tagChanges).map)
        }

        permissions?.let { value ->
            val prepared = value
                .map { it.key to JsonValue.wrap(it.value) }
                .toTypedArray()

            channel.put(PERMISSIONS, jsonMapOf(*prepared))
        }

        val identityHits = jsonMapOf(
            USER_ID_KEY to userId,
        )

        // Full payload
        return jsonMapOf(
            CHANNEL_KEY to channel.build(),
            IDENTITY_HINTS_KEY to if (identityHits.isEmpty) null else identityHits
        ).toJsonValue()
    }

    public fun equals(payload: ChannelRegistrationPayload?, compareIsActive: Boolean): Boolean {
        if (payload == null) {
            return false
        }

        if (compareIsActive && payload.isActive != isActive) {
            return false
        }

        return optIn == payload.optIn
                && backgroundEnabled == payload.backgroundEnabled
                && setTags == payload.setTags
                && ObjectsCompat.equals(deviceType, payload.deviceType)
                && ObjectsCompat.equals(pushAddress, payload.pushAddress)
                && ObjectsCompat.equals(tags, payload.tags)
                && ObjectsCompat.equals(tagChanges, payload.tagChanges)
                && ObjectsCompat.equals(userId, payload.userId)
                && ObjectsCompat.equals(timezone, payload.timezone)
                && ObjectsCompat.equals(language, payload.language)
                && ObjectsCompat.equals(country, payload.country)
                && ObjectsCompat.equals(locationSettings, payload.locationSettings)
                && ObjectsCompat.equals(appVersion, payload.appVersion)
                && ObjectsCompat.equals(sdkVersion, payload.sdkVersion)
                && ObjectsCompat.equals(deviceModel, payload.deviceModel)
                && ObjectsCompat.equals(apiVersion, payload.apiVersion)
                && ObjectsCompat.equals(deliveryType, payload.deliveryType)
                && ObjectsCompat.equals(contactId, payload.contactId)
                && ObjectsCompat.equals(permissions, payload.permissions)
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val that = o as ChannelRegistrationPayload
        return equals(that, true)
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            optIn,
            backgroundEnabled,
            deviceType,
            pushAddress,
            setTags,
            tags,
            tagChanges,
            userId,
            timezone,
            language,
            country,
            locationSettings,
            appVersion,
            sdkVersion,
            deviceModel,
            apiVersion,
            deliveryType,
            contactId,
            permissions
        )
    }

    override fun toString(): String {
        return "ChannelRegistrationPayload{optIn=$optIn, backgroundEnabled=$backgroundEnabled, " +
                "deviceType='$deviceType', pushAddress='$pushAddress', setTags=$setTags, tags=$tags, " +
                "tagChanges=$tagChanges, userId='$userId', timezone='$timezone', language='$language', " +
                "country='$country', locationSettings=$locationSettings, appVersion='$appVersion', " +
                "sdkVersion='$sdkVersion', deviceModel='$deviceModel', apiVersion=$apiVersion, " +
                "deliveryType='$deliveryType', " +
                "contactId='$contactId', isActive=$isActive, permissions=$permissions}"
    }

    internal companion object {

        const val CHANNEL_KEY: String = "channel"
        const val DEVICE_TYPE_KEY: String = "device_type"
        const val OPT_IN_KEY: String = "opt_in"
        const val BACKGROUND_ENABLED_KEY: String = "background"
        const val PUSH_ADDRESS_KEY: String = "push_address"
        const val SET_TAGS_KEY: String = "set_tags"
        const val TAGS_KEY: String = "tags"
        const val TAG_CHANGES_KEY: String = "tag_changes"
        const val TAG_CHANGES_ADD_KEY: String = "add"
        const val TAG_CHANGES_REMOVE_KEY: String = "remove"
        const val IDENTITY_HINTS_KEY: String = "identity_hints"
        const val USER_ID_KEY: String = "user_id"
        const val TIMEZONE_KEY: String = "timezone"
        const val LANGUAGE_KEY: String = "locale_language"
        const val COUNTRY_KEY: String = "locale_country"
        const val LOCATION_SETTINGS_KEY: String = "location_settings"
        const val APP_VERSION_KEY: String = "app_version"
        const val SDK_VERSION_KEY: String = "sdk_version"
        const val DEVICE_MODEL_KEY: String = "device_model"
        const val API_VERSION_KEY: String = "android_api_version"
        const val CONTACT_ID_KEY: String = "contact_id"
        const val ANDROID_EXTRAS_KEY: String = "android"
        const val ANDROID_DELIVERY_TYPE: String = "delivery_type"
        const val IS_ACTIVE: String = "is_activity"
        const val PERMISSIONS: String = "permissions"

        /**
         * Creates a ChannelRegistrationPayload from JSON object
         *
         * @param value The JSON object to create the ChannelRegistrationPayload from
         * @return The payload as a ChannelRegistrationPayload
         */
        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): ChannelRegistrationPayload {
            val jsonMap = value.requireMap()

            val channelJson = jsonMap.requireMap(CHANNEL_KEY)
            val identityHints = jsonMap.optionalMap(IDENTITY_HINTS_KEY)

            if (channelJson.isEmpty && identityHints?.isEmpty == true) {
                throw JsonException("Invalid channel payload: $value")
            }

            val tags = channelJson.opt(TAGS_KEY).optList().map { it.requireString() }.toSet()
            val tagChanges = channelJson.opt(TAG_CHANGES_KEY).optMap()

            val locationSettings = channelJson[LOCATION_SETTINGS_KEY]?.getBoolean(false)
            val apiVersion = channelJson[API_VERSION_KEY]?.getInt(-1)


            val deliveryType = channelJson
                .optionalMap(ANDROID_EXTRAS_KEY)
                ?.optionalField<JsonValue>(ANDROID_DELIVERY_TYPE)
                ?.let {
                    PushProvider.DeliveryType.fromJson(it)
                }

            val permissions = channelJson
                .optionalMap(PERMISSIONS)
                ?.mapNotNull {
                    val stringValue = it.value.string ?: return@mapNotNull null
                    it.key to stringValue
                }
                ?.toMap()

            return Builder()
                .setOptIn(channelJson.opt(OPT_IN_KEY).getBoolean(false))
                .setBackgroundEnabled(channelJson.opt(BACKGROUND_ENABLED_KEY).getBoolean(false))
                .setDeviceType(channelJson[DEVICE_TYPE_KEY]?.let(DeviceType::fromJson))
                .setPushAddress(channelJson.optionalField(PUSH_ADDRESS_KEY))
                .setLanguage(channelJson.optionalField(LANGUAGE_KEY))
                .setCountry(channelJson.optionalField(COUNTRY_KEY))
                .setTimezone(channelJson.optionalField(TIMEZONE_KEY))
                .setTags(channelJson.opt(SET_TAGS_KEY).getBoolean(false), tags)
                .setTagChanges(if (tagChanges.isEmpty) null else tagChanges)
                .setUserId(identityHints?.optionalField(USER_ID_KEY))
                .setLocationSettings(locationSettings)
                .setAppVersion(channelJson.optionalField(APP_VERSION_KEY))
                .setSdkVersion(channelJson.optionalField(SDK_VERSION_KEY))
                .setDeviceModel(channelJson.optionalField(DEVICE_MODEL_KEY))
                .setApiVersion(apiVersion)
                .setContactId(channelJson.optionalField(CONTACT_ID_KEY))
                .setIsActive(channelJson.opt(IS_ACTIVE).getBoolean(false))
                .setPermissions(permissions)
                .setDeliveryType(deliveryType)
                .build()
        }
    }
}
