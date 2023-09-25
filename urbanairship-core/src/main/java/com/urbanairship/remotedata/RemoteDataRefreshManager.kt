/* Copyright Airship and Contributors */

package com.urbanairship.remotedata

import com.urbanairship.AirshipDispatchers
import com.urbanairship.PrivacyManager
import com.urbanairship.job.JobDispatcher
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

internal class RemoteDataRefreshManager(
    private val jobDispatcher: JobDispatcher,
    private val privacyManager: PrivacyManager,
    val providers: List<RemoteDataProvider>
) {

    // This is not really needed but helps prevent spam logs about canceling a task since
    // we use replace when dispatching work
    private val refreshPending = AtomicBoolean(false)

    private val _refreshFlow: MutableSharedFlow<Pair<RemoteDataSource, RemoteDataProvider.RefreshResult>> = MutableSharedFlow()
    val refreshFlow: SharedFlow<Pair<RemoteDataSource, RemoteDataProvider.RefreshResult>> = _refreshFlow.asSharedFlow()

    suspend fun performRefresh(
        changeToken: String,
        locale: Locale,
        randomValue: Int,
    ): JobResult {
        refreshPending.set(false)
        return withContext(AirshipDispatchers.IO) {
            if (!privacyManager.isAnyFeatureEnabled) {
                providers.forEach {
                    _refreshFlow.emit(Pair(it.source, RemoteDataProvider.RefreshResult.SKIPPED))
                }
                return@withContext JobResult.SUCCESS
            }

            val result = providers.map {
                async {
                    val result = it.refresh(changeToken, locale, randomValue)
                    _refreshFlow.emit(Pair(it.source, result))
                    result
                }
            }.awaitAll()

            if (result.contains(RemoteDataProvider.RefreshResult.FAILED)) {
                JobResult.RETRY
            } else {
                JobResult.SUCCESS
            }
        }
    }

    fun dispatchRefreshJob() {
        if (!refreshPending.compareAndSet(false, true)) {
            return
        }

        val jobInfo = JobInfo.newBuilder()
            .setAction(RemoteData.ACTION_REFRESH)
            .setNetworkAccessRequired(true)
            .setAirshipComponent(RemoteData::class.java)
            .setConflictStrategy(JobInfo.REPLACE)
            .build()

        jobDispatcher.dispatch(jobInfo)
    }
}
