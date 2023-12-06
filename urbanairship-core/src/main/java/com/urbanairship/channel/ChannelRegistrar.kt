/* Copyright Airship and Contributors */

package com.urbanairship.channel

import android.content.Context
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.channel.AirshipChannel.Extender.Blocking
import com.urbanairship.channel.AirshipChannel.Extender.Suspending
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.json.tryParse
import com.urbanairship.util.Clock
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class ChannelRegistrar(
    private val dataStore: PreferenceDataStore,
    private val channelApiClient: ChannelApiClient,
    private val activityMonitor: ActivityMonitor,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
) {
    constructor(
        context: Context,
        dataStore: PreferenceDataStore,
        runtimeConfig: AirshipRuntimeConfig,
    ) : this(
        dataStore = dataStore,
        channelApiClient = ChannelApiClient(runtimeConfig),
        activityMonitor = GlobalActivityMonitor.shared(context)
    )

    private val _channelIdFlow: MutableStateFlow<String?> = MutableStateFlow(this.channelId)
    val channelIdFlow: StateFlow<String?> = _channelIdFlow.asStateFlow()

    internal var channelId: String?
        get() = dataStore.getString(CHANNEL_ID_KEY, null)
        private set(value) = dataStore.put(CHANNEL_ID_KEY, value)

    private val channelRegistrationPayloadExtenders: MutableList<AirshipChannel.Extender> = CopyOnWriteArrayList()

    internal fun addChannelRegistrationPayloadExtender(extender: AirshipChannel.Extender) {
        channelRegistrationPayloadExtenders.add(extender)
    }

    internal fun removeChannelRegistrationPayloadExtender(extender: AirshipChannel.Extender) {
        channelRegistrationPayloadExtenders.remove(extender)
    }

    internal suspend fun updateRegistration(): RegistrationResult {
        return channelId?.let { updateChannel(it) } ?: createChannel()
    }

    private suspend fun buildCraPayload(): ChannelRegistrationPayload {
        var builder = ChannelRegistrationPayload.Builder()
        for (extender in channelRegistrationPayloadExtenders) {
            builder = when (extender) {
                is Suspending -> extender.extend(builder)
                is Blocking -> extender.extend(builder)
            }
        }
        return builder.build()
    }

    private fun shouldUpdate(
        payload: ChannelRegistrationPayload,
        lastRegistrationInfo: RegistrationInfo?,
        location: String
    ): Boolean {
        if (lastRegistrationInfo == null || lastRegistrationInfo.location != location) {
            return true
        }

        val timeSinceLastRegistration = clock.currentTimeMillis() - lastRegistrationInfo.dateMillis
        if (timeSinceLastRegistration < 0) {
            return true
        }

        if (activityMonitor.isAppForegrounded && timeSinceLastRegistration > CHANNEL_REREGISTRATION_INTERVAL_MS) {
            return true
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
            !shouldUpdate(
                    buildCraPayload(),
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
        val payload = buildCraPayload()
        val result = channelApiClient.createChannel(payload)

        UALog.i { "Channel registration finished with result: $result" }

        return if (result.isSuccessful && result.value != null) {
            UALog.i { "Airship channel created: ${result.value.identifier}" }
            this.channelId = result.value.identifier
            this.lastChannelRegistrationInfo = RegistrationInfo(
                dateMillis = clock.currentTimeMillis(),
                payload = payload,
                location = result.value.location
            )

            _channelIdFlow.tryEmit(result.value.identifier)
            if (isUpToDate()) {
                RegistrationResult.SUCCESS
            } else {
                RegistrationResult.NEEDS_UPDATE
            }
        } else if (result.isServerError || result.isTooManyRequestsError || result.exception != null) {
            return RegistrationResult.FAILED
        } else {
            return RegistrationResult.SUCCESS
        }
    }

    private suspend fun updateChannel(channelId: String): RegistrationResult {
        val payload = buildCraPayload()
        val updatePayload = minimizeUpdatePayload(channelId, payload)
        if (updatePayload == null) {
            UALog.v { "Channel already up to date." }
            return RegistrationResult.SUCCESS
        }

        val result = channelApiClient.updateChannel(channelId, updatePayload)

        UALog.i { "Channel registration finished with result $result" }

        return if (result.isSuccessful && result.value != null) {
            UALog.i { "Airship channel updated" }
            // Set non-minimized payload as the last sent version, for future comparison
            this.lastChannelRegistrationInfo = RegistrationInfo(
                dateMillis = clock.currentTimeMillis(),
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
    val payload: ChannelRegistrationPayload,
    /**
     * The location of the channel. We track this so we can detect URL changes if the site is
     * being migrated. When it changes we do a full CRA request.
     */
    val location: String
) : JsonSerializable {

    constructor(json: JsonMap) : this(
        dateMillis = json.requireField<Long>(DATE),
        payload = ChannelRegistrationPayload.fromJson(json.require(PAYLOAD)),
        location = json.requireField<String>(LOCATION),
    )

    override fun toJsonValue(): JsonValue = jsonMapOf(
        DATE to dateMillis,
        PAYLOAD to payload,
        LOCATION to location,
    ).toJsonValue()

    private companion object {

        private const val DATE = "date"
        private const val PAYLOAD = "payload"
        private const val LOCATION = "location"
    }
}
