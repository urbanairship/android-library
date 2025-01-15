/* Copyright Airship and Contributors */

package com.urbanairship.audience

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.cache.AirshipCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class HashChecker (
    private val cache: AirshipCache,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
){

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    public suspend fun evaluate(
        hashSelector: AudienceHashSelector?,
        deviceInfoProvider: DeviceInfoProvider
    ): AirshipDeviceAudienceResult {

        if (hashSelector == null) {
            return AirshipDeviceAudienceResult.match
        }

        val operation = scope.async {
            val contactId = deviceInfoProvider.getStableContactInfo().contactId
            val channelId = deviceInfoProvider.getChannelId()

            val result = resolveResult(
                selector = hashSelector,
                contactId = contactId,
                channelId = channelId
            )

            cacheResult(
                selector = hashSelector,
                result = result,
                contactId = contactId,
                channelId = channelId
            )

            return@async result
        }

        return operation.await()
    }

    private suspend fun resolveResult(
        selector: AudienceHashSelector,
        contactId: String,
        channelId: String
    ): AirshipDeviceAudienceResult {

        val cached = getCachedResult(
            selector = selector,
            contactId = contactId,
            channelId = channelId)

        if (cached != null) {
            return cached
        }

        val isMatch = selector.evaluate(channelId = channelId, contactId = contactId)
        val reporting = selector.sticky?.reportingMetadata?.let { listOf(it) }

        return AirshipDeviceAudienceResult(isMatch, reporting)
    }

    private suspend fun getCachedResult(
        selector: AudienceHashSelector,
        contactId: String,
        channelId: String
    ): AirshipDeviceAudienceResult? {

        val sticky = selector.sticky ?: return null
        val key = makeCacheKey(id = sticky.id, contactId = contactId, channelId = channelId )

        return cache.getCached(key, AirshipDeviceAudienceResult::fromJson)
    }

    private suspend fun cacheResult(
        selector: AudienceHashSelector,
        result: AirshipDeviceAudienceResult,
        contactId: String,
        channelId: String
    ) {

        val sticky = selector.sticky ?: return
        val key = makeCacheKey(id = sticky.id, contactId = contactId, channelId = channelId)

        cache.store(
            value = result,
            key = key,
            ttl = sticky.lastAccessTtl
        )
    }

    private companion object {
        fun makeCacheKey(id: String, contactId: String, channelId: String): String {
            return listOf("StickyHash", contactId, channelId, id).joinToString(":")
        }
    }
}
