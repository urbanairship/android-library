package com.urbanairship.cache

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.UAirship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipCache @JvmOverloads constructor(
    context: Context,
    runtimeConfig: AirshipRuntimeConfig,
    isPersistent: Boolean = true,
    private val appVersion: String = UAirship.getAppVersion().toString(),
    private val sdkVersion: String = UAirship.getVersion(),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    private val store: CacheDao
    private val dbScope = CoroutineScope(dispatcher + SupervisorJob())

    init {
        store = if (isPersistent) {
            CacheDatabase.persistent(context, runtimeConfig.configOptions.appKey).cacheDao()
        } else {
            CacheDatabase.inMemory(context).cacheDao()
        }

        dbScope.launch {
            try {
                deleteExpired()
            } catch (ex: Exception) {
                UALog.e(ex) { "Failed to clear expired cache items" }
            }
        }
    }

    public suspend fun <T> getCached(key: String, converter: (JsonValue) -> T): T? {
        val stored = store.getEntryWithKey(key) ?: return null
        if (stored.appVersion != appVersion ||
            stored.sdkVersion != sdkVersion ||
            stored.isExpired(clock.currentTimeMillis())) {
            store.deleteItemWithKey(key)
            return null
        }

        return try {
            converter.invoke(stored.data)
        } catch (ex: Exception) {
            UALog.e(ex) { "Failed to restore data from cache" }
            null
        }
    }

    public suspend fun store(value: JsonSerializable, key: String, ttl: ULong) {
        store.updateEntry(
            CacheEntity(
                key = key,
                appVersion = appVersion,
                sdkVersion = sdkVersion,
                data = value.toJsonValue(),
                expireOn = clock.currentTimeMillis() + ttl.toLong()
            )
        )
    }

    internal suspend fun deleteExpired(
        appVersion: String = this.appVersion,
        sdkVersion: String = this.sdkVersion,
        timestamp: Long = clock.currentTimeMillis()
    ) {
        store.deleteExpired(
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            timestamp = timestamp
        )
    }
}
