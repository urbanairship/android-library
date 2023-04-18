/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import android.net.Uri
import androidx.annotation.OpenForTesting
import androidx.annotation.VisibleForTesting
import com.urbanairship.Logger
import com.urbanairship.channel.AttributeMutation
import com.urbanairship.channel.TagGroupsMutation
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestSession
import com.urbanairship.http.Response
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils
import com.urbanairship.util.PlatformUtils
import com.urbanairship.util.UAHttpStatusUtil
import java.util.Locale
import java.util.TimeZone

// TODO: Remove public open once we port tests to kotlin

/**
 * A high level abstraction for performing Contact API requests.
 * @hide
 */
@OpenForTesting
public open class ContactApiClient @VisibleForTesting constructor(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: RequestSession
) {

    public constructor(runtimeConfig: AirshipRuntimeConfig) : this(
        runtimeConfig, runtimeConfig.requestSession
    )

    @Throws(RequestException::class)
    public open fun resolve(channelId: String): Response<ContactIdentity?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(RESOLVE_PATH).build()

        val payload = jsonMapOf(
            CHANNEL_ID to channelId, DEVICE_TYPE to PlatformUtils.getDeviceType(
                runtimeConfig.platform
            )
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                JsonValue.parseString(responseBody).requireMap().let {
                    val contactId = it.require(CONTACT_ID).requireString()
                    val isAnonymous = it.opt(IS_ANONYMOUS).getBoolean(false)
                    ContactIdentity(contactId, isAnonymous, null)
                }
            } else {
                null
            }
        }
    }

    @Throws(RequestException::class)
    public open fun identify(
        namedUserId: String,
        channelId: String,
        contactId: String?
    ): Response<ContactIdentity?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(IDENTIFY_PATH).build()

        val payload = jsonMapOf(
            NAMED_USER_ID to namedUserId,
            CHANNEL_ID to channelId,
            DEVICE_TYPE to PlatformUtils.getDeviceType(
                runtimeConfig.platform
            ),
            CONTACT_ID to contactId
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                val parsedContactID =
                    JsonValue.parseString(responseBody).requireMap().require(CONTACT_ID)
                        .requireString()
                ContactIdentity(parsedContactID, false, namedUserId)
            } else {
                null
            }
        }
    }

    @Throws(RequestException::class)
    public open fun registerEmail(
        identifier: String,
        emailAddress: String,
        options: EmailRegistrationOptions
    ): Response<AssociatedChannel?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(EMAIL_PATH).build()

        val payload = jsonMapOf(CHANNEL_KEY to jsonMapOf(TYPE to "email",
            ADDRESS to emailAddress,
            TIMEZONE to TimeZone.getDefault().id,
            LOCALE_LANGUAGE to Locale.getDefault().language,
            LOCALE_COUNTRY to Locale.getDefault().country,

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
        }, PROPERTIES_KEY to options.properties
        )

        return registerAndAssociate(identifier, url, payload, ChannelType.EMAIL)
    }

    @Throws(RequestException::class)
    public open fun registerSms(
        identifier: String,
        msisdn: String,
        options: SmsRegistrationOptions
    ): Response<AssociatedChannel?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(SMS_PATH).build()

        // TODO this should take in the locale from locale manager
        val payload = jsonMapOf(
            MSISDN_KEY to msisdn,
            SENDER_KEY to options.senderId,
            TIMEZONE to TimeZone.getDefault().id,
            LOCALE_LANGUAGE to Locale.getDefault().language,
            LOCALE_COUNTRY to Locale.getDefault().country
        )

        return registerAndAssociate(identifier, url, payload, ChannelType.SMS)
    }

    @Throws(RequestException::class)
    public open fun registerOpenChannel(
        identifier: String,
        address: String,
        options: OpenChannelRegistrationOptions
    ): Response<AssociatedChannel?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(OPEN_CHANNEL_PATH).build()

        // TODO this should take in the locale from locale manager
        val payload = jsonMapOf(
            CHANNEL_KEY to jsonMapOf(
                TYPE_KEY to "open",
                OPT_IN_KEY to true,
                ADDRESS to address,
                TIMEZONE to TimeZone.getDefault().id,
                LOCALE_LANGUAGE to Locale.getDefault().language,
                LOCALE_COUNTRY to Locale.getDefault().country,
                OPEN_KEY to jsonMapOf(
                    PLATFORM_NAME_KEY to options.platformName,
                    IDENTIFIERS_KEY to options.identifiers,
                )
            )
        )

        return registerAndAssociate(identifier, url, payload, ChannelType.OPEN)
    }

    @Throws(RequestException::class)
    public open fun associatedChannel(
        contactId: String,
        channelId: String,
        channelType: ChannelType
    ): Response<AssociatedChannel?> {
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
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            Logger.verbose("Update contact response status: %s body: %s", status, responseBody)
            if (status == 200) {
                AssociatedChannel(channelId, channelType)
            } else {
                null
            }
        }
    }

    @Throws(RequestException::class)
    public open fun reset(channelId: String): Response<ContactIdentity?> {
        val url = runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(RESET_PATH).build()

        val payload = jsonMapOf(
            CHANNEL_ID to channelId, DEVICE_TYPE to PlatformUtils.getDeviceType(
                runtimeConfig.platform
            )
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (UAHttpStatusUtil.inSuccessRange(status)) {
                val contactId =
                    JsonValue.parseString(responseBody).optMap().opt(CONTACT_ID).requireString()
                ContactIdentity(contactId, true, null)
            } else {
                null
            }
        }
    }

    @Throws(RequestException::class)
    public open fun update(
        identifier: String,
        tagGroupMutations: List<TagGroupsMutation>,
        attributeMutations: List<AttributeMutation>,
        subscriptionListMutations: List<ScopedSubscriptionListMutation>
    ): Response<Void?> {
        val url =
            runtimeConfig.urlConfig.deviceUrl().appendEncodedPath(UPDATE_PATH + identifier).build()

        val payload = jsonMapOf(
            TAGS to tagGroupMutations.tagsPayload(),
            ATTRIBUTES to attributeMutations.ifEmpty { null },
            SUBSCRIPTION_LISTS to subscriptionListMutations.ifEmpty { null }
        )

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            Logger.verbose("Update contact response status: %s body: %s", status, responseBody)
            null
        }
    }

    @Throws(RequestException::class)
    private fun registerAndAssociate(
        contactID: String,
        url: Uri?,
        payload: JsonSerializable,
        channelType: ChannelType
    ): Response<AssociatedChannel?> {
        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "POST",
            auth = RequestAuth.BasicAppAuth,
            body = RequestBody.Json(payload),
            headers = headers
        )

        val channelResponse =
            session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
                if (UAHttpStatusUtil.inSuccessRange(status)) {
                    JsonValue.parseString(responseBody).optMap().opt(CHANNEL_ID).requireString()
                } else {
                    null
                }
            }

        return if (channelResponse.isSuccessful && channelResponse.result != null) {
            associatedChannel(contactID, channelResponse.result, channelType)
        } else {
            channelResponse.map { null }
        }
    }

    /**
     * Fetches the current set of subscriptions for the contact.
     *
     * @return The response.
     * @throws RequestException
     */
    @Throws(RequestException::class)
    public open fun getSubscriptionLists(identifier: String): Response<Map<String, Set<Scope>>?> {
        val url = runtimeConfig.urlConfig.deviceUrl()
            .appendEncodedPath(SUBSCRIPTION_LIST_PATH + identifier).build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url, method = "GET", auth = RequestAuth.BasicAppAuth, headers = headers
        )

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            Logger.verbose(
                "Fetch contact subscription list status: %s body: %s", status, responseBody
            )

            if (UAHttpStatusUtil.inSuccessRange(status)) {
                val json =
                    JsonValue.parseString(responseBody).requireMap().require(SUBSCRIPTION_LISTS_KEY)
                        .requireList()

                val subscriptionLists = mutableMapOf<String, MutableSet<Scope>>()
                json.map { entryJson ->
                    val scope = Scope.fromJson(entryJson.optMap().opt(SCOPE_KEY))
                    for (listIdJson in entryJson.optMap().opt(LIST_IDS_KEY).optList()) {
                        val listId = listIdJson.requireString()
                        var scopes = subscriptionLists[listId]
                        if (scopes == null) {
                            scopes = HashSet()
                            subscriptionLists[listId] = scopes
                        }
                        scopes.add(scope)
                    }
                }

                return@execute subscriptionLists.mapValues { it.value.toSet() }.toMap()
            } else {
                return@execute null
            }
        }
    }

    private companion object {

        private const val RESOLVE_PATH = "api/contacts/resolve/"
        private const val IDENTIFY_PATH = "api/contacts/identify/"
        private const val RESET_PATH = "api/contacts/reset/"
        private const val UPDATE_PATH = "api/contacts/"
        private const val EMAIL_PATH = "api/channels/restricted/email/"
        private const val SMS_PATH = "api/channels/restricted/sms/"
        private const val OPEN_CHANNEL_PATH = "api/channels/restricted/open/"
        private const val SUBSCRIPTION_LIST_PATH = "api/subscription_lists/contacts/"
        private const val SUBSCRIPTION_LISTS_KEY = "subscription_lists"
        private const val SCOPE_KEY = "scope"
        private const val LIST_IDS_KEY = "list_ids"
        private const val NAMED_USER_ID = "named_user_id"
        private const val CHANNEL_ID = "channel_id"
        private const val CHANNEL_KEY = "channel"
        private const val DEVICE_TYPE = "device_type"
        private const val TYPE = "type"
        private const val CONTACT_ID = "contact_id"
        private const val IS_ANONYMOUS = "is_anonymous"
        private const val TAGS = "tags"
        private const val ATTRIBUTES = "attributes"
        private const val SUBSCRIPTION_LISTS = "subscription_lists"
        private const val TIMEZONE = "timezone"
        private const val ADDRESS = "address"
        private const val LOCALE_COUNTRY = "locale_country"
        private const val LOCALE_LANGUAGE = "locale_language"
        private const val MSISDN_KEY = "msisdn"
        private const val SENDER_KEY = "sender"
        private const val OPTED_IN_KEY = "opted_in"
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
