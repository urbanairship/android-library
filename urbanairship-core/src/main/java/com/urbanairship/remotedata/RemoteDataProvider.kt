/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import androidx.annotation.VisibleForTesting
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.http.RequestResult
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.json.tryParse
import com.urbanairship.util.Clock
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal abstract class RemoteDataProvider(
    var source: RemoteDataSource,
    private val remoteDataStore: RemoteDataStore,
    private val preferenceDataStore: PreferenceDataStore,
    private val defaultEnabled: Boolean = true,
    private val clock: Clock = Clock.DEFAULT_CLOCK
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

    private val _statusUpdates = MutableStateFlow(RemoteData.Status.OUT_OF_DATE)
    val statusUpdates: StateFlow<RemoteData.Status> = _statusUpdates.asStateFlow()

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

    fun isCurrent(locale: Locale, randomValue: Int, remoteDataInfo: RemoteDataInfo): Boolean {
        if (!isEnabled) {
            return false
        }

        if (remoteDataInfo != lastRefreshState?.remoteDataInfo) {
            UALog.v { "Remote data info $remoteDataInfo does not match last refresh state ${lastRefreshState?.remoteDataInfo}" }

            return false
        }

        val refreshState = this.lastRefreshState ?: return false
        return isRemoteDataInfoUpToDate(refreshState.remoteDataInfo, locale, randomValue)
    }

    fun notifyOutdated(remoteDataInfo: RemoteDataInfo): Boolean {
        lastRefreshStateLock.withLock {
            return if (this.lastRefreshState?.remoteDataInfo == remoteDataInfo) {
                this.lastRefreshState = null
                true
            } else {
                false
            }
        }
    }

    // / Refreshes data. Assumes no reentry.
    suspend fun refresh(changeToken: String, locale: Locale, randomValue: Int): RefreshResult {
        if (!this.isEnabled) {
            if (this.remoteDataStore.deletePayloads() > 0) {
                return RefreshResult.NewData()
            }

            return RefreshResult.Skipped()
        }

        val refreshState = this.lastRefreshState

        val currentState = this.status(
            refreshState = refreshState,
            changeToken = changeToken,
            locale = locale,
            randomValue = randomValue
        )
        notifyNewStatus(currentState)

        if (currentState == RemoteData.Status.UP_TO_DATE) {
            return RefreshResult.Skipped()
        }

        val result = fetchRemoteData(locale, randomValue, refreshState?.remoteDataInfo)
        if (result.exception != null) {
            return RefreshResult.Failed()
        }

        if (result.isSuccessful && result.value != null) {
            UALog.v { "Updating remote data store for source $source" }
            remoteDataStore.deletePayloads()
            remoteDataStore.savePayloads(result.value.payloads)
            this.lastRefreshState = LastRefreshState(
                changeToken,
                result.value.remoteDataInfo,
                clock.currentTimeMillis()
            )

            return RefreshResult.NewData()
        }

        if (result.status == 304) {
            if (refreshState == null) {
                UALog.e { "Received a 304 without a previous refresh state" }
                return RefreshResult.Failed()
            }

            this.lastRefreshState = LastRefreshState(
                changeToken,
                refreshState.remoteDataInfo,
                clock.currentTimeMillis()
            )
            return RefreshResult.Skipped()
        }

        return RefreshResult.Failed()
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

    fun status(token: String, locale: Locale, randomValue: Int): RemoteData.Status {
        val result = status(lastRefreshState, token, locale, randomValue)
        notifyNewStatus(result)
        return result
    }

    private fun notifyNewStatus(state: RemoteData.Status) {
        _statusUpdates.update { state }
    }

    private fun status(
        refreshState: LastRefreshState?,
        changeToken: String,
        locale: Locale,
        randomValue: Int
    ): RemoteData.Status {
        if (!this.isEnabled) {
            return RemoteData.Status.OUT_OF_DATE
        }

        if (refreshState == null) {
            return RemoteData.Status.OUT_OF_DATE
        }

        if (clock.currentTimeMillis() >= refreshState.timeMillis + MAX_STALE_TIME_MS) {
            return RemoteData.Status.OUT_OF_DATE
        }

        if (!this.isRemoteDataInfoUpToDate(refreshState.remoteDataInfo, locale, randomValue)) {
            return RemoteData.Status.OUT_OF_DATE
        }

        if (refreshState.changeToken != changeToken) {
            return RemoteData.Status.STALE
        }

        return RemoteData.Status.UP_TO_DATE
    }

    private data class LastRefreshState(
        val changeToken: String,
        val remoteDataInfo: RemoteDataInfo,
        val timeMillis: Long
    ) : JsonSerializable {
        constructor(json: JsonValue) : this(
            changeToken = json.requireMap().requireField("changeToken"),
            remoteDataInfo = RemoteDataInfo(json.requireMap().require("remoteDataInfo")),
            timeMillis = json.requireMap().optionalField("timeMilliseconds") ?: 0
        )
        override fun toJsonValue(): JsonValue = jsonMapOf(
            "changeToken" to changeToken,
            "remoteDataInfo" to remoteDataInfo,
            "timeMilliseconds" to timeMillis
        ).toJsonValue()
    }

    internal sealed interface RefreshResult {
        data class Skipped(private val id: Long = System.nanoTime()) : RefreshResult

        data class NewData(private val id: Long = System.nanoTime()) : RefreshResult

        data class Failed(
            val error: Throwable? = null,
            private val id: Long = System.nanoTime()
        ) : RefreshResult
    }

    companion object {
        val MAX_STALE_TIME_MS: Long = TimeUnit.DAYS.toMillis(3)
    }
}
