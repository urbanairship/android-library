package com.urbanairship.automation.audiencecheck

import com.urbanairship.UALog
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.automation.AudienceCheckOverrides
import com.urbanairship.cache.AirshipCache
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.http.RequestException
import com.urbanairship.json.JsonValue
import com.urbanairship.remoteconfig.AdditionalAudienceCheckConfig
import com.urbanairship.util.SerialQueue

internal class AdditionalAudienceCheckerResolver internal constructor(
    private val config: AirshipRuntimeConfig,
    private val cache: AirshipCache,
    private val apiClient: AdditionalAudienceCheckApiClient = AdditionalAudienceCheckApiClient(config)
) {
    private val queue = SerialQueue()

    private val audienceCheckConfig: AdditionalAudienceCheckConfig?
        get() = config.remoteConfig.iaaConfig?.additionalAudienceCheck

    suspend fun resolve(
        deviceInfoProvider: DeviceInfoProvider,
        audienceCheckOverrides: AudienceCheckOverrides?
    ): Result<Boolean> {
        val config = audienceCheckConfig ?: return Result.success(true)
        if (!config.isEnabled) { return Result.success(true) }

        val url = audienceCheckOverrides?.url
            ?: config.url
            ?: return Result.failure(
                IllegalArgumentException("Missing additional audience check url")
            )

        if (audienceCheckOverrides?.bypass == true) {
            UALog.v { "Additional audience check is bypassed " }
            return Result.success(true)
        }

        return queue.run {
            doResolve(
                url = url,
                context = audienceCheckOverrides?.context ?: config.context,
                deviceInfoProvider = deviceInfoProvider
            )
        }
    }

    private suspend fun doResolve(
        url: String,
        context: JsonValue?,
        deviceInfoProvider: DeviceInfoProvider
    ): Result<Boolean> {
        val channelId = deviceInfoProvider.getChannelId()
        val contactInfo = deviceInfoProvider.getStableContactInfo()

        val cacheKey = cacheKey(url, context ?: JsonValue.NULL, contactInfo.contactId, channelId)

        cache.getCached(cacheKey, AdditionalAudienceCheckApiClient.Result::fromJson)?.let {
            return Result.success(it.isMatched)
        }

        val response = apiClient.resolve(
            info = AdditionalAudienceCheckApiClient.Info(
                url = url,
                channelId = channelId,
                contactId = contactInfo.contactId,
                namedUserId = contactInfo.namedUserId,
                context = context
            )
        )

        val result = response.value

        return if (response.isSuccessful && result != null) {
            cache.store(result, cacheKey, result.cacheTtl.inWholeMilliseconds.toULong())
            Result.success(result.isMatched)
        } else if (response.isServerError) {
            Result.failure(RequestException("Server error"))
        } else {
            Result.success(false)
        }
    }

    private fun cacheKey(
        url: String,
        context: JsonValue,
        contactId: String,
        channelId: String): String {
        return listOf(url, context.toString(true), contactId, channelId).joinToString(":")
    }
}
