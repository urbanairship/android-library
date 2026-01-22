/* Copyright Airship and Contributors */

package com.urbanairship.util

import com.urbanairship.AirshipDispatchers
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn

internal abstract class AutoRefreshingDataProvider<T, R>(
    private val identifierUpdates: Flow<String>,
    private val overrideUpdates: Flow<R>,
    clock: Clock = Clock.DEFAULT_CLOCK,
    private val taskSleeper: TaskSleeper = TaskSleeper.default,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
) {

    data class IdentifiableResult<T>(
        val identifier: String,
        val data: Result<T>
    )

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val changeTokenFlow = MutableStateFlow(UUID.randomUUID())
    private val fetchCache = FetchCache<T>(clock, maxCacheAge)

    fun refresh() {
        changeTokenFlow.value = UUID.randomUUID()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val updates: SharedFlow<IdentifiableResult<T>> by lazy {
        combine(identifierUpdates, changeTokenFlow) { identifier, changeToken ->
            Pair(identifier, changeToken)
        }.flatMapLatest { (identifier, changeToken) ->
            var hasEmittedForThisId = false
            var backoff: Duration = initialBackoff

            val fetchUpdates = flow {
                while (true) {
                    val fetched = fetch(identifier, changeToken)
                    backoff = if (fetched.data.isSuccess) {
                        emit(fetched)
                        hasEmittedForThisId = true
                        taskSleeper.sleep(fetchCache.remainingCacheTimeMillis)
                        initialBackoff
                    } else {
                        // Only emit failure if we haven't successfully
                        // provided data for this specific ID yet
                        if (!hasEmittedForThisId) {
                            emit(fetched)
                        }
                        taskSleeper.sleep(backoff)
                        backoff.times(2).coerceAtMost(maxBackoff)
                    }
                }
            }


            combine(fetchUpdates, overrideUpdates) { fetchUpdate, overrides ->
                IdentifiableResult(
                    identifier = fetchUpdate.identifier, data = fetchUpdate.data.fold(onSuccess = {
                        Result.success(onApplyOverrides(it, overrides))
                    }, onFailure = {
                        Result.failure(it)
                    })
                )
            }
        }.shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 100),
            replay = 1
        )
    }

    private suspend fun fetch(identifier: String, changeToken: UUID): IdentifiableResult<T> {
        val cached = fetchCache.getCache(identifier, changeToken)
        if (cached != null) {
            return IdentifiableResult(
                identifier = identifier,
                data = cached
            )
        }

        val result = onFetch(identifier)
        if (result.isSuccess) {
            fetchCache.setCache(identifier, changeToken, result)
        }

        return IdentifiableResult(
            identifier = identifier,
            data = result
        )
    }

    abstract suspend fun onFetch(identifier: String): Result<T>
    abstract fun onApplyOverrides(data: T, overrides: R): T

    companion object {
        private val initialBackoff = 8.seconds
        private val maxBackoff = 64.seconds
        private val maxCacheAge = 10.minutes
    }

    private class FetchCache<T>(private val clock: Clock, private val maxCacheAge: Duration) {

        private val cachedResponse = CachedValue<Triple<String, UUID, Result<T>>>(clock)

        fun getCache(contactId: String, changeToken: UUID): Result<T>? {
            val cached = cachedResponse.get()
            if (cached != null && cached.first == contactId && cached.second == changeToken) {
                return cached.third
            }

            return null
        }

        fun setCache(contactId: String, changeToken: UUID, value: Result<T>) {
            cachedResponse.set(
                Triple(contactId, changeToken, value),
                clock.currentTimeMillis() + maxCacheAge.inWholeMilliseconds
            )
        }

        val remainingCacheTimeMillis: Duration
            get() = cachedResponse.remainingCacheTimeMillis().milliseconds
    }
}
