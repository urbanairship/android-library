/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import com.google.android.gms.common.util.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import com.urbanairship.json.tryParse
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.withContext

internal abstract class RemoteDataProvider(
    var source: RemoteDataSource,
    private val remoteDataStore: RemoteDataStore,
    private val preferenceDataStore: PreferenceDataStore,
    private val defaultEnabled: Boolean = true,
) {
    private val enabledKey: String = "RemoteDataProvider.${source.name}_enabled"
    private val lastRefreshStateKey: String = "RemoteDataProvider.${source.name}_refresh_state"
    private val lastRefreshStateLock = ReentrantLock()

    var isEnabled: Boolean
    get() {
        return preferenceDataStore.getBoolean(enabledKey, defaultEnabled)
    }
    set(value) {
        preferenceDataStore.put(enabledKey, value)
    }

    private var lastRefreshState: LastRefreshState?
        get() {
            return lastRefreshStateLock.withLock {
                preferenceDataStore.getJsonValue(lastRefreshStateKey).tryParse {
                    LastRefreshState(it)
                }
            }
        }
        set(value) {
            return lastRefreshStateLock.withLock {
                preferenceDataStore.put(lastRefreshStateKey, value)
            }
        }

    suspend fun payloads(type: List<String>): Set<RemoteDataPayload> {
        return withContext(AirshipDispatchers.IO) {
            if (isEnabled) {
                remoteDataStore.getPayloads(type)
            } else {
                emptySet()
            }
        }
    }

    protected fun clearLastRefreshState() {
        this.lastRefreshState = null
    }

    fun isCurrent(locale: Locale, randomValue: Int): Boolean {
        if (!isEnabled) {
            return false
        }

        val refreshState = this.lastRefreshState ?: return false
        return isRemoteDataInfoUpToDate(refreshState.remoteDataInfo, locale, randomValue)
    }

    fun notifyOutdated(remoteDataInfo: RemoteDataInfo) {
        lastRefreshStateLock.withLock {
            if (this.lastRefreshState?.remoteDataInfo == remoteDataInfo) {
                this.lastRefreshState = null
            }
        }
    }

    // / Refreshes data. Assumes no reentry.
    suspend fun refresh(changeToken: String, locale: Locale, randomValue: Int): RefreshResult {
        if (!this.isEnabled) {
            if (this.remoteDataStore.deletePayloads() > 0) {
                return RefreshResult.NEW_DATA
            }

            return RefreshResult.SKIPPED
        }

        val refreshState = this.lastRefreshState

        val shouldRefresh = this.shouldRefresh(
            refreshState = refreshState,
            changeToken = changeToken,
            locale = locale,
            randomValue = randomValue
        )

        if (!shouldRefresh) {
            return RefreshResult.SKIPPED
        }

        val result = fetchRemoteData(locale, randomValue, refreshState?.remoteDataInfo)
        if (result.exception != null) {
            return RefreshResult.FAILED
        }

        if (result.isSuccessful && result.value != null) {
            remoteDataStore.deletePayloads()
            remoteDataStore.savePayloads(result.value.payloads)
            this.lastRefreshState = LastRefreshState(
                changeToken,
                result.value.remoteDataInfo
            )

            return RefreshResult.NEW_DATA
        }

        if (result.status == 304) {
            if (refreshState == null) {
                UALog.e { "Received a 304 without a previous refresh state" }
                return RefreshResult.FAILED
            }

            this.lastRefreshState = LastRefreshState(
                changeToken,
                refreshState.remoteDataInfo
            )
            return RefreshResult.SKIPPED
        }

        return RefreshResult.FAILED
    }

    @VisibleForTesting
    abstract fun isRemoteDataInfoUpToDate(
        remoteDataInfo: RemoteDataInfo,
        locale: Locale,
        randomValue: Int
    ): Boolean

    @VisibleForTesting
    abstract suspend fun fetchRemoteData(
        locale: Locale,
        randomValue: Int,
        lastRemoteDataInfo: RemoteDataInfo?
    ): RequestResult<RemoteDataApiClient.Result>

    private suspend fun shouldRefresh(
        refreshState: LastRefreshState?,
        changeToken: String,
        locale: Locale,
        randomValue: Int
    ): Boolean {
        if (refreshState == null) {
            return true
        }

        if (refreshState.changeToken != changeToken) {
            return true
        }

        return !this.isRemoteDataInfoUpToDate(refreshState.remoteDataInfo, locale, randomValue)
    }

    private data class LastRefreshState(
        val changeToken: String,
        val remoteDataInfo: RemoteDataInfo
    ) : JsonSerializable {
        constructor(json: JsonValue) : this(
            changeToken = json.requireMap().requireField("changeToken"),
            remoteDataInfo = RemoteDataInfo(json.requireMap().require("remoteDataInfo"))
        )
        override fun toJsonValue(): JsonValue = jsonMapOf(
            "changeToken" to changeToken,
            "remoteDataInfo" to remoteDataInfo
        ).toJsonValue()
    }

    internal enum class RefreshResult {
        SKIPPED,
        NEW_DATA,
        FAILED
    }
}
