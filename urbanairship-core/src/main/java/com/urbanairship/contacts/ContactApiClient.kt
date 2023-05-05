/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.net.Uri
import com.urbanairship.Logger
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.http.log
import com.urbanairship.http.toSuspendingRequestSession
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.util.Clock
import com.urbanairship.util.DateUtils
import com.urbanairship.util.PlatformUtils
import com.urbanairship.util.UAHttpStatusUtil
import com.urbanairship.util.UAStringUtil
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * A high level abstraction for performing Contact API requests.
 * @hide
 */
@OpenForTesting
internal class ContactApiClient constructor(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession = runtimeConfig.requestSession.toSuspendingRequestSession(),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val nonceTokenFactory: () -> String = { UUID.randomUUID().toString() }
) {

    @Throws(RequestException::class)
    suspend fun resolve(channelId: String, contactId: String?): RequestResult<IdentityResult> {
        val action = jsonMapOf(
            CONTACT_ID to contactId,
            TYPE_KEY to "resolve"
        )
        return performIdentify(channelId, action)
    }

    @Throws(RequestException::class)
    suspend fun identify(
        channelId: String,
        contactId: String?,
        namedUserId: String,
    ): RequestResult<IdentityResult> {
        val action = jsonMapOf(
            NAMED_USER_ID to namedUserId,
            TYPE_KEY to "identify",
            CONTACT_ID to contactId
        )

        return performIdentify(channelId, action)
    }

    @Throws(RequestException::class)
    suspend fun reset(channelId: String): RequestResult<IdentityResult> {
        val action = jsonMapOf(
            TYPE_KEY to "reset"
        )

        return performIdentify(channelId, action)
    }

    suspend fun registerEmail(
        contactId: String,
        emailAddress: String,
        options: EmailRegistrationOptions,
        locale: Locale
    ): RequestResult<AssociatedChannel> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(EMAIL_PATH).build()

        val payload = jsonMapOf(
            CHANNEL_KEY to jsonMapOf(
                TYPE to "email",
                ADDRESS to emailAddress,
                TIMEZONE to TimeZone.getDefault().id,
                LOCALE_LANGUAGE to locale.language,
                LOCALE_COUNTRY to locale.country,

                COMMERCIAL_OPTED_IN_KEY to options.commercialOptedIn.let {
                    if (it > 0) {
                        DateUtils.createIso8601TimeStamp(it)
                    } else {
                        null
                    }
                },

                TRANSACTIONAL_OPTED_IN_KEY to options.transactionalOptedIn.let {
                    if (it > 0) {
                        DateUtils.createIso8601TimeStamp(it)
                    } else {
                        null
                    }
                }), OPT_IN_MODE_KEY to options.isDoubleOptIn.let {
                if (options.isDoubleOptIn) {
                    OPT_IN_DOUBLE
                } else {
                    OPT_IN_CLASSIC
                }
            },
            PROPERTIES_KEY to options.properties
        )

        return registerAndAssociate(contactId, url, payload, ChannelType.EMAIL)
    }

    @Throws(RequestException::class)
    suspend fun registerSms(
        contactId: String,
        msisdn: String,
        options: SmsRegistrationOptions,
        locale: Locale
    ): RequestResult<AssociatedChannel> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(SMS_PATH).build()

        val payload = jsonMapOf(
            MSISDN_KEY to msisdn,
            SENDER_KEY to options.senderId,
            TIMEZONE to TimeZone.getDefault().id,
            LOCALE_LANGUAGE to locale.language,
            LOCALE_COUNTRY to locale.country
        )

        return registerAndAssociate(contactId, url, payload, ChannelType.SMS)
    }

    @Throws(RequestException::class)
    suspend fun registerOpen(
        contactId: String,
        address: String,
        options: OpenChannelRegistrationOptions,
        locale: Locale
    ): RequestResult<AssociatedChannel> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(OPEN_CHANNEL_PATH).build()

        val payload = jsonMapOf(
            CHANNEL_KEY to jsonMapOf(
                TYPE_KEY to "open",
                OPT_IN_KEY to true,
                ADDRESS to address,
                TIMEZONE to TimeZone.getDefault().id,
                LOCALE_LANGUAGE to locale.language,
                LOCALE_COUNTRY to locale.country,
                OPEN_KEY to jsonMapOf(
                    PLATFORM_NAME_KEY to options.platformName,
                    IDENTIFIERS_KEY to options.identifiers,
                )
            )
        )

        return registerAndAssociate(contactId, url, payload, ChannelType.OPEN)
    }

    @Throws(RequestException::class)
    suspend fun associatedChannel(
        contactId: String,
        channelId: String,
        channelType: ChannelType
    ): RequestResult<AssociatedChannel> {
        val url =
            runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(UPDATE_PATH + contactId).build()

        val payload = jsonMapOf(
            ASSOCIATE_KEY to listOf(
                jsonMapOf(
                    DEVICE_TYPE to channelType.toString().lowercase(), CHANNEL_ID to channelId
                )
            )
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.ContactTokenAuth(contactId),
            body = RequestBody.Json(payload),
            headers = headers
        )

        Logger.d { "Associating channel $channelId type $channelType request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, _: String? ->
            if (status == 200) {
                AssociatedChannel(channelId, channelType)
            } else {
                null
            }
        }.also { result ->
            result.log { "Association channel $channelId type $channelType result: $result" }
        }
    }

    @Throws(RequestException::class)
    suspend fun update(
        contactId: String,
        tagGroupMutations: List<TagGroupsMutation>?,
        attributeMutations: List<AttributeMutation>?,
        subscriptionListMutations: List<ScopedSubscriptionListMutation>?
    ): RequestResult<Unit> {
        val url =
            runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(UPDATE_PATH + contactId).build()

        val payload = jsonMapOf(
            TAGS to tagGroupMutations?.tagsPayload(),
            ATTRIBUTES to attributeMutations?.ifEmpty { null },
            SUBSCRIPTION_LISTS to subscriptionListMutations?.ifEmpty { null }
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.ContactTokenAuth(contactId),
            body = RequestBody.Json(payload),
            headers = headers
        )

        Logger.d { "Updating contact $contactId request: $request" }

        return session.execute(request).also { result ->
            result.log { "Updating contact $contactId result: $result" }
        }
    }

    private suspend fun registerAndAssociate(
        contactId: String,
        url: Uri?,
        payload: JsonSerializable,
        channelType: ChannelType
    ): RequestResult<AssociatedChannel> {
        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey,
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        Logger.d { "Registering channel $channelType request: $request" }

        val result =
            session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
                if (UAHttpStatusUtil.inSuccessRange(status)) {
                    JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID).requireString()
                } else {
                    null
                }
            }.also { result ->
                result.log { "Registering channel $channelType result: $result" }
            }

        val channelId = result.value ?: return result.map { null }
        return associatedChannel(contactId, channelId, channelType)
    }

    private suspend fun performIdentify(
        channelId: String,
        requestAction: JsonSerializable
    ): RequestResult<IdentityResult> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(IDENTIFY_PATH).build()

        val requestTime = clock.currentTimeMillis()
        val nonce = nonceTokenFactory()
        val timestamp = DateUtils.createIso8601TimeStamp(requestTime)

        val payload = jsonMapOf(
            DEVICE_INFO to jsonMapOf(
                DEVICE_TYPE to PlatformUtils.getDeviceType(
                    runtimeConfig.platform
                )
            ), ACTION_KEY to requestAction
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;",
            "X-UA-Channel-ID" to channelId,
            "X-UA-Appkey" to runtimeConfig.configOptions.appKey,
            "X-UA-Nonce" to nonce,
            "X-UA-Timestamp" to timestamp
        )

        val token = try {
            UAStringUtil.generateSignedToken(
                runtimeConfig.configOptions.appSecret, listOf(
                    runtimeConfig.configOptions.appKey, channelId, nonce, timestamp
                )
            )
        } catch (e: Exception) {
            return RequestResult(exception = e)
        }

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BearerToken(token),
            body = RequestBody.Json(payload),
            headers = headers
        )

        Logger.d { "Identifying contact for channel $channelId request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                IdentityResult(JsonValue.parseString(responseBody).requireMap(), clock)
            } else {
                null
            }
        }.also { result ->
            result.log { "Identifying contact for channel $channelId result: $result" }
        }
    }

    private companion object {

        private const val IDENTIFY_PATH = "api/contacts/identify/v2"
        private const val UPDATE_PATH = "api/contacts/"
        private const val EMAIL_PATH = "api/channels/restricted/email/"
        private const val SMS_PATH = "api/channels/restricted/sms/"
        private const val OPEN_CHANNEL_PATH = "api/channels/restricted/open/"
        private const val NAMED_USER_ID = "named_user_id"
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_KEY = "channel"
        private const val DEVICE_TYPE = "device_type"
        private const val DEVICE_INFO = "device_info"
        private const val ACTION_KEY = "action"
        private const val TYPE = "type"
        private const val CONTACT_ID = "contact_id"
        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        private const val TIMEZONE = "timezone"
        private const val ADDRESS = "address"
        private const val LOCALE_COUNTRY = "locale_country"
        private const val LOCALE_LANGUAGE = "locale_language"
        private const val MSISDN_KEY = "msisdn"
        private const val SENDER_KEY = "sender"
        private const val OPT_IN_MODE_KEY = "opt_in_mode"
        private const val OPT_IN_CLASSIC = "classic"
        private const val OPT_IN_DOUBLE = "double"
        private const val TYPE_KEY = "type"
        private const val OPT_IN_KEY = "opt_in"
        private const val OPEN_KEY = "open"
        private const val PLATFORM_NAME_KEY = "open_platform_name"
        private const val IDENTIFIERS_KEY = "identifiers"
        private const val ASSOCIATE_KEY = "associate"
        private const val COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in"
        private const val TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in"
        private const val PROPERTIES_KEY = "properties"
    }

    internal data class IdentityResult(
        val contactId: String,
        val isAnonymous: Boolean,
        val channelAssociatedDateMs: Long,
        val token: String,
        val tokenExpiryDateMs: Long
    ) {
        constructor(jsonMap: JsonMap, clock: Clock) : this(
            contactId = jsonMap.requireField<JsonMap>("contact").requireField("contact_id"),
            isAnonymous = jsonMap.requireField<JsonMap>("contact").requireField("is_anonymous"),
            channelAssociatedDateMs = DateUtils.parseIso8601(
                jsonMap.requireField<JsonMap>("contact")
                    .requireField<String>("channel_association_timestamp")
            ),
            token = jsonMap.requireField("token"),
            tokenExpiryDateMs = clock.currentTimeMillis() + jsonMap.requireField<Long>("token_expires_in")
        )
    }
}

private fun List<TagGroupsMutation>.tagsPayload(): JsonMap? {
    val add = mutableMapOf<String, MutableSet<String>>()
    val remove = mutableMapOf<String, MutableSet<String>>()
    val set = mutableMapOf<String, MutableSet<String>>()

    this.forEach { mutation ->
        mutation.addTags.forEach { entry ->
            add.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
        mutation.removeTags.forEach { entry ->
            remove.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
        mutation.setTags.forEach { entry ->
            set.getOrPut(entry.key) { mutableSetOf() }.addAll(entry.value)
        }
    }

    if (add.isEmpty() && remove.isEmpty() && set.isEmpty()) {
        return null
    }

    return jsonMapOf(
        "add" to add.ifEmpty { null },
        "remove" to remove.ifEmpty { null },
        "set" to set.ifEmpty { null }
    )
}
