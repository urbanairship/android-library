/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.cache.AirshipCache
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Feature Flag result cache. A cache managed by the app that is optionally used by [FeatureFlagManager.flag]
 * to return an existing value if the flag fails to resolve or no longer exists.
 */
public class FeatureFlagResultCache @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP) constructor(
    private val cache: AirshipCache,
) {
    private val pendingResultScope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())

    /**
     * Caches a flag.
     * @param flag The flag to cache.
     * @param ttl The cache time to live.
     */
    public suspend fun cache(flag: FeatureFlag, ttl: Duration) {
        val key = makeKey(flag.name) ?: return
        this.cache.store(flag, key, ttl)
    }

    /**
     * Gets a flag from the cache if it exists.
     * @param name The flag name.
     * @return The flag if it exists, otherwise null.
     */
    public suspend fun flag(name: String): FeatureFlag? {
        val key = makeKey(name) ?: return null
        return this.cache.getCached(key) {
            FeatureFlag.fromJson(it)
        }
    }

    /**
     * Removes a flag from the cache.
     * @param name The flag name.
     */
    public suspend fun removeCachedFlag(name: String) {
        val key = makeKey(name) ?: return
        return this.cache.delete(key)
    }

    /**
     * Caches a flag in an async method. See [cache]
     * @param flag The flag to cache.
     * @param ttlMilliseconds The cache time to live in milliseconds.
     */
    public fun cacheAsync(flag: FeatureFlag, ttlMilliseconds: ULong) {
        pendingResultScope.launch {
            cache(flag, ttlMilliseconds.toLong().milliseconds)
        }
    }

    /**
     * Gets a flag as a pending result. See [flag].
     * @param name The name of the flag.
     * @return A pending result.
     */
    public fun flagAsync(name: String): PendingResult<FeatureFlag> {
        val pendingResult = PendingResult<FeatureFlag>()
        pendingResultScope.launch {
            pendingResult.setResult(flag(name))
        }
        return pendingResult
    }

    /**
     * Removes a cached flag as an async method. See [removeCachedFlag]
     * @param name The flag name.
     */
    public fun removeCachedFlagAsync(name: String) {
        pendingResultScope.launch {
            removeCachedFlag(name)
        }
    }

    private fun makeKey(key: String): String? {
        if (key.isEmpty()) {
            return null
        }
        return "FeatureFlagResultCache:$key"
    }
}
