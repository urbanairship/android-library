/* Copyright Airship and Contributors */

package com.urbanairship.channel

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.PreferenceDataStore
import com.urbanairship.PrivacyManager
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.json.tryParse
import com.urbanairship.util.Clock
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Interface for providing channel creation method. Default method is [ChannelGenerationMethod.Automatic]
 */
public interface AirshipChannelCreateOption {
    public fun get(): ChannelGenerationMethod
}

/**
 * Defines methods of channel id generation. [Automatic] means to use a regular channel creation logic
 * [Restore] will try to restore the provided channel id if it's valid. If the provided channel id is invalid
 * [Automatic] method will be used
 */
public sealed class ChannelGenerationMethod {
    public data object Automatic: ChannelGenerationMethod()
    public class Restore(internal val channelID: String): ChannelGenerationMethod()

    internal val isValid: Boolean
        get() {
            return when(this) {
                Automatic -> true
                is Restore -> {
                    try {
                        UUID.fromString(channelID)
                        true
                    } catch (ex: Exception) {
                        false
                    }
                }
            }
        }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ChannelRegistrar(
    private val dataStore: PreferenceDataStore,
    private val channelApiClient: ChannelApiClient,
    private val activityMonitor: ActivityMonitor,
    private val channelCreateOption: AirshipChannelCreateOption? = null,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val privacyManager: PrivacyManager
) {
    public constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
        privacyManager: PrivacyManager
    ) : this(
        dataStore = dataStore,
        channelApiClient = ChannelApiClient(runtimeConfig),
        activityMonitor = GlobalActivityMonitor.shared(context),
        channelCreateOption = runtimeConfig.configOptions.channelCreateOption,
        privacyManager = privacyManager
    )

    internal var payloadBuilder: (suspend () -> ChannelRegistrationPayload)? = null

    private val _channelIdFlow: MutableStateFlow<String?> = MutableStateFlow(this.channelId)
    internal val channelIdFlow: StateFlow<String?> = _channelIdFlow.asStateFlow()

    internal var channelId: String?
        get() = dataStore.getString(CHANNEL_ID_KEY, null)
        private set(value) = dataStore.put(CHANNEL_ID_KEY, value)


    internal suspend fun updateRegistration(): RegistrationResult {
        return channelId?.let { updateChannel(it) } ?: createChannel()
    }

    private suspend fun buildCraPayload(): ChannelRegistrationPayload? {
        return payloadBuilder?.invoke()
    }

    private fun shouldUpdate(
        payload: ChannelRegistrationPayload,
        lastRegistrationInfo: RegistrationInfo?,
        location: String
    ): Boolean {
        if (lastRegistrationInfo == null || lastRegistrationInfo.location != location) {
            return true
        }

        if (privacyManager.isAnyFeatureEnabled) {
            val timeSinceLastRegistration =
                clock.currentTimeMillis() - lastRegistrationInfo.dateMillis
            if (timeSinceLastRegistration < 0) {
                return true
            }

            if (activityMonitor.isAppForegrounded && timeSinceLastRegistration > CHANNEL_REREGISTRATION_INTERVAL_MS) {
                return true
            }
        }

        return !payload.equals(lastRegistrationInfo.payload, false)
    }

    private fun minimizeUpdatePayload(
        channelId: String,
        payload: ChannelRegistrationPayload
    ): ChannelRegistrationPayload? {
        val lastRegistrationInfo = this.lastChannelRegistrationInfo ?: return payload
        val location = channelApiClient.createLocation(channelId).toString()

        if (location != lastRegistrationInfo.location) {
            return payload
        }

        val lastFullUpload = lastRegistrationInfo.lastFullUploadMillis
        if (lastFullUpload == null || (clock.currentTimeMillis() - lastFullUpload) > CHANNEL_REREGISTRATION_INTERVAL_MS) {
            return payload
        }

        return if (shouldUpdate(payload, lastRegistrationInfo, location)) {
            payload.minimizedPayload(lastRegistrationInfo.payload)
        } else {
            null
        }
    }

    private suspend fun isUpToDate(): Boolean {
        val channelId = channelId
        return if (channelId == null) {
            false
        } else {
            val payload = buildCraPayload() ?: return true
            !shouldUpdate(
                payload,
                lastChannelRegistrationInfo,
                channelApiClient.createLocation(channelId).toString()
            )
        }
    }

    private var lastChannelRegistrationInfo: RegistrationInfo?
        get() = dataStore.optJsonValue(LAST_CHANNEL_REGISTRATION_INFO)?.tryParse {
            RegistrationInfo(it.requireMap())
        }
        set(value) = dataStore.put(LAST_CHANNEL_REGISTRATION_INFO, value)

    private suspend fun createChannel(): RegistrationResult {
        val payload = buildCraPayload() ?: return RegistrationResult.FAILED

        val method = channelCreateOption?.get() ?: ChannelGenerationMethod.Automatic

        val result = when (method) {
            ChannelGenerationMethod.Automatic -> regularCreateChannel(payload)
            is ChannelGenerationMethod.Restore -> {
                var restoreResult: RegistrationResult? = null
                if (method.isValid) {
                    restoreResult = tryRestoreChannel(method.channelID, payload)
                }

                if (restoreResult == null) {
                    restoreResult = regularCreateChannel(payload)
                }

                restoreResult
            }
        }

        return result
    }

    private suspend fun tryRestoreChannel(
        channelId: String,
        payload: ChannelRegistrationPayload
    ): RegistrationResult? {

        val location = channelApiClient.createLocation(channelId) ?: return null

        val response = RequestResult(
            status = 200,
            value = Channel(
                identifier = channelId,
                location = location.toString()),
            body = null,
            headers = null
        )

        onNewChannelIdCreated(response, payload, rememberPayload = false)
        return updateChannel(channelId)
    }

    private suspend fun regularCreateChannel(payload: ChannelRegistrationPayload): RegistrationResult {
        val result = channelApiClient.createChannel(payload)

        UALog.i { "Channel registration finished with result: $result" }

        return onNewChannelIdCreated(result, payload)
    }

    private suspend fun onNewChannelIdCreated(
        response: RequestResult<Channel>,
        payload: ChannelRegistrationPayload,
        rememberPayload: Boolean = true
    ): RegistrationResult {
        return if (response.isSuccessful && response.value != null) {
            UALog.i { "Airship channel created: ${response.value.identifier}" }
            this.channelId = response.value.identifier
            if (rememberPayload) {
                this.lastChannelRegistrationInfo = RegistrationInfo(
                    dateMillis = clock.currentTimeMillis(),
                    lastFullUploadMillis = clock.currentTimeMillis(),
                    payload = payload,
                    location = response.value.location
                )
            }

            _channelIdFlow.tryEmit(response.value.identifier)
            if (isUpToDate()) {
                RegistrationResult.SUCCESS
            } else {
                RegistrationResult.NEEDS_UPDATE
            }
        } else if (response.isServerError || response.isTooManyRequestsError || response.exception != null) {
            return RegistrationResult.FAILED
        } else {
            return RegistrationResult.SUCCESS
        }
    }

    private suspend fun updateChannel(channelId: String): RegistrationResult {
        val payload = buildCraPayload() ?: return RegistrationResult.FAILED
        val updatePayload = minimizeUpdatePayload(channelId, payload)
        if (updatePayload == null) {
            UALog.v { "Channel already up to date." }
            return RegistrationResult.SUCCESS
        }

        val result = channelApiClient.updateChannel(channelId, updatePayload)

        UALog.i { "Channel registration finished with result $result" }
        val fullUploadMillis = if (payload == updatePayload) {
            clock.currentTimeMillis()
        } else {
            lastChannelRegistrationInfo?.lastFullUploadMillis
        }

        return if (result.isSuccessful && result.value != null) {
            UALog.i { "Airship channel updated" }
            // Set non-minimized payload as the last sent version, for future comparison
            this.lastChannelRegistrationInfo = RegistrationInfo(
                dateMillis = clock.currentTimeMillis(),
                lastFullUploadMillis = fullUploadMillis,
                payload = payload,
                location = result.value.location
            )
            if (isUpToDate()) {
                RegistrationResult.SUCCESS
            } else {
                RegistrationResult.NEEDS_UPDATE
            }
        } else if (result.status == 409) {
            UALog.d { "Channel registration conflict, will recreate channel." }
            this.lastChannelRegistrationInfo = null
            this.channelId = null
            return createChannel()
        } else if (result.isServerError || result.isTooManyRequestsError || result.exception != null) {
            RegistrationResult.FAILED
        } else {
            RegistrationResult.SUCCESS
        }
    }

    private companion object {

        private const val CHANNEL_ID_KEY = "com.urbanairship.push.CHANNEL_ID"
        private const val LAST_CHANNEL_REGISTRATION_INFO = "com.urbanairship.channel.LAST_CHANNEL_REGISTRATION_INFO"
        private const val CHANNEL_REREGISTRATION_INTERVAL_MS: Long = 24 * 60 * 60 * 1000 // 24H
    }
}

internal enum class RegistrationResult {
    FAILED, SUCCESS, NEEDS_UPDATE
}

private data class RegistrationInfo(
    val dateMillis: Long,
    val lastFullUploadMillis: Long?,
    val payload: ChannelRegistrationPayload,
    /**
     * The location of the channel. We track this so we can detect URL changes if the site is
     * being migrated. When it changes we do a full CRA request.
     */
    val location: String
) : JsonSerializable {

    constructor(json: JsonMap) : this(
        dateMillis = json.requireField<Long>(DATE),
        lastFullUploadMillis = json.optionalField<Long>(LAST_FULL_UPLOAD_DATE),
        payload = ChannelRegistrationPayload.fromJson(json.require(PAYLOAD)),
        location = json.requireField<String>(LOCATION),
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        DATE to dateMillis,
        LAST_FULL_UPLOAD_DATE to lastFullUploadMillis,
        PAYLOAD to payload,
        LOCATION to location,
    ).toJsonValue()

    private companion object {

        private const val DATE = "date"
        private const val LAST_FULL_UPLOAD_DATE = "last_full_upload_date"
        private const val PAYLOAD = "payload"
        private const val LOCATION = "location"
    }
}
