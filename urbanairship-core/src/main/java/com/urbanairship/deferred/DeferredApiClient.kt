package com.urbanairship.deferred

import android.net.Uri
import com.urbanairship.audience.AudienceOverrides
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.Request
import com.urbanairship.http.RequestAuth
import com.urbanairship.http.RequestBody
import com.urbanairship.http.RequestResult
import com.urbanairship.http.ResponseParser
import com.urbanairship.http.SuspendingRequestSession
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.PlatformUtils
import com.urbanairship.util.UAHttpStatusUtil

internal class DeferredApiClient(
    private val config: AirshipRuntimeConfig,
    private val session: SuspendingRequestSession
) {

    suspend fun resolve(
        uri: Uri,
        channelId: String,
        contactId: String?,
        stateOverrides: StateOverrides,
        audienceOverrides: AudienceOverrides.Channel?,
        triggerContext: DeferredTriggerContext?
    ): RequestResult<JsonValue> {
        val request = Request(
            url = uri,
            method = "POST",
            auth = RequestAuth.ChannelTokenAuth(channelId),
            headers = mapOf("Accept" to "application/vnd.urbanairship+json; version=3;"),
            body = RequestBody.Json(jsonMapOf(
                KEY_PLATFORM to PlatformUtils.asString(config.platform),
                KEY_CHANNEL_ID to channelId,
                KEY_CONTACT_ID to contactId,
                KEY_STATE_OVERRIDES to stateOverrides,
                KEY_TRIGGER_CONTEXT to triggerContext,
                KEY_TAG_OVERRIDES to JsonValue.wrapOpt(audienceOverrides?.tags),
                KEY_ATTRIBUTES_OVERRIDES to JsonValue.wrapOpt(audienceOverrides?.attributes)
            ))
        )

        return session.execute(request, ResponseParser { status, _, responseBody ->
            if (!UAHttpStatusUtil.inSuccessRange(status)) {
                return@ResponseParser JsonValue.NULL
            }

            JsonValue.parseString(responseBody)
        })
    }

    private companion object {
        const val KEY_PLATFORM = "platform"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_CONTACT_ID = "contact_id"
        const val KEY_STATE_OVERRIDES = "state_overrides"
        const val KEY_TRIGGER_CONTEXT = "trigger"
        const val KEY_TAG_OVERRIDES = "tag_overrides"
        const val KEY_ATTRIBUTES_OVERRIDES = "attribute_overrides"
    }
}
