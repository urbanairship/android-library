/* Copyright Airship and Contributors */

package com.urbanairship.contacts

import com.urbanairship.UALog
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.http.log
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.isoDateAsMilliseconds
import com.urbanairship.json.requireField
import com.urbanairship.util.UAHttpStatusUtil

internal class ContactChannelsApiClient(
    private val runtimeConfig: AirshipRuntimeConfig,
    private val session: RequestSession = runtimeConfig.requestSession
) {

    internal suspend fun fetch(contactId: String): RequestResult<List<ContactChannel>> {
        val url = runtimeConfig.deviceUrl.appendEncodedPath(PATH + contactId).build()

        val headers = mapOf(
            "Accept" to "application/vnd.urbanairship+json; version=3;"
        )

        val request = Request(
            url = url,
            method = "GET",
            auth = RequestAuth.ContactTokenAuth(contactId),
            headers = headers
        )

        UALog.d { "Fetching contact channels for $contactId request: $request" }

        return session.execute(request) { status: Int, _: Map<String, String>, responseBody: String? ->
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return@execute null
            }

            return@execute parseChannels(responseBody)
        }.also { result ->
            result.log { "Fetching contact channels for $contactId finished with result: $result" }
        }
    }

    @Throws(JsonException::class)
    private fun parseChannels(responseBody: String?): List<ContactChannel> {
        return JsonValue.parseString(responseBody).requireMap().require(CHANNELS_KEY).requireList()
            .map { it.requireMap() }
            .mapNotNull { map ->
                when(val type: String = map.requireField(TYPE_KEY)) {
                    EMAIL_TYPE -> {
                        ContactChannel.Email(
                            ContactChannel.Email.RegistrationInfo.Registered(
                                channelId = map.requireField(CHANNEL_ID_KEY),
                                maskedAddress = map.requireField(EMAIL_ADDRESS_KEY),
                                commercialOptedIn = map.isoDateAsMilliseconds(COMMERCIAL_OPTED_IN_KEY),
                                commercialOptedOut = map.isoDateAsMilliseconds(COMMERCIAL_OPTED_OUT_KEY),
                                transactionalOptedIn = map.isoDateAsMilliseconds(TRANSACTIONAL_OPTED_IN_KEY),
                                transactionalOptedOut = map.isoDateAsMilliseconds(TRANSACTIONAL_OPTED_OUT_KEY)
                            )
                        )
                    }
                    SMS_TYPE -> {
                        ContactChannel.Sms(
                            ContactChannel.Sms.RegistrationInfo.Registered(
                                channelId = map.requireField(CHANNEL_ID_KEY),
                                maskedAddress = map.requireField(MSISDN_KEY),
                                isOptIn = map.requireField(OPT_IN_KEY),
                                senderId =  map.requireField(SENDER_KEY)
                            )
                        )
                    }
                    else -> {
                        UALog.w("Unrecognized contact channel type $type")
                        null
                    }
                }
        }
    }

    private companion object {
        private const val PATH = "api/contacts/associated_types/"
        private const val CHANNELS_KEY = "channels"

        // Shared
        private const val CHANNEL_ID_KEY = "channel_id"
        private const val TYPE_KEY = "type"

        // Email
        private const val EMAIL_TYPE = "email"
        private const val EMAIL_ADDRESS_KEY = "email_address"
        private const val COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in"
        private const val COMMERCIAL_OPTED_OUT_KEY = "commercial_opted_out"
        private const val TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in"
        private const val TRANSACTIONAL_OPTED_OUT_KEY = "transactional_opted_out"

        // SMS
        private const val SMS_TYPE = "sms"
        private const val OPT_IN_KEY = "opt_in"
        private const val SENDER_KEY = "sender"
        private const val MSISDN_KEY = "msisdn"
    }
}
