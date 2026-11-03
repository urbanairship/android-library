package com.urbanairship.cache

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.core.content.pm.PackageInfoCompat
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.Airship
import com.urbanairship.config.AirshipRuntimeConfig
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock
import com.urbanairship.util.minus
import com.urbanairship.util.plus
import java.time.Instant
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class AirshipCache(
    context: Context,
    runtimeConfig: AirshipRuntimeConfig,
    isPersistent: Boolean = true,
    appVersion: String? = null,
    private val sdkVersion: String = Airship.version,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.IO
) {

    @set:VisibleForTesting
    internal var store: CacheDao = if (isPersistent) {
        CacheDatabase.persistent(context, runtimeConfig.configOptions.appKey).cacheDao()
    } else {
        CacheDatabase.inMemory(context).cacheDao()
    }

    private val dbScope = CoroutineScope(dispatcher + SupervisorJob())

    private val appVersion = appVersion
        ?: context.packageManager.getPackageInfo(context.packageName, 0)
        ?.let { PackageInfoCompat.getLongVersionCode(it) }
        ?.toString() ?: "-1"

    init {
        dbScope.launch {
            try {
                deleteExpired()
            } catch (e: Exception) {
                UALog.e(e) { "Failed to clear expired cache items" }
            }
        }
    }

    public suspend fun <T> getCached(key: String, converter: (JsonValue) -> T): T? {
        val stored = guardDb(null, { "Failed to read cache entry for key: $key" }) { store.getEntryWithKey(key) }
            ?: return null
        if (stored.appVersion != appVersion ||
            stored.sdkVersion != sdkVersion ||
            stored.isExpired(clock.now())) {
            guardDb(Unit, { "Failed to delete stale cache entry for key: $key" }) { store.deleteItemWithKey(key) }
            return null
        }

        return try {
            converter.invoke(stored.data)
        } catch (e: Exception) {
            UALog.e(e) { "Failed to restore data from cache" }
            null
        }
    }

    public suspend fun delete(key: String) {
        guardDb(Unit, { "Failed to delete cache entry for key: $key" }) { store.deleteItemWithKey(key) }
    }

    public suspend fun store(value: JsonSerializable, key: String, ttl: Duration) {
        guardDb(Unit, { "Failed to store cache entry for key: $key" }) {
            store.updateEntry(
                CacheEntity(
                    key = key,
                    appVersion = appVersion,
                    sdkVersion = sdkVersion,
                    data = value.toJsonValue(),
                    expireOn = clock.now() + ttl
                )
            )
        }
    }

    /**
     * Runs [block], logging and returning [default] instead of letting [SQLiteException] crash.
     *
     * @param default The value to return if [block] throws [SQLiteException].
     * @param message Describes the failure for the log, evaluated only if [block] throws.
     * @param block The database operation to run.
     */
    private suspend inline fun <T> guardDb(default: T, noinline message: () -> String, block: () -> T): T {
        return try {
            block()
        } catch (e: SQLiteException) {
            UALog.e(e, message)
            default
        }
    }

    internal suspend fun deleteExpired(
        appVersion: String = this.appVersion,
        sdkVersion: String = this.sdkVersion,
        timestamp: Instant = clock.now()
    ) {
        store.deleteExpired(
            appVersion = appVersion,
            sdkVersion = sdkVersion,
            timestamp = timestamp
        )
    }
}
