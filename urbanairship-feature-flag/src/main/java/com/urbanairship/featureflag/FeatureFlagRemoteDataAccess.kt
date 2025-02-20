package com.urbanairship.featureflag

import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Clock
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class FeatureFlagRemoteDataAccess(
    private val remoteData: RemoteData,
    private val clock: Clock = Clock.DEFAULT_CLOCK
) {

    companion object {
        private const val PAYLOAD_TYPE = "feature_flags"
        private val MAX_TIMEOUT_MILLIS: Long = TimeUnit.SECONDS.toMillis(15)
    }

    val status: RemoteData.Status
        get() {
            return remoteData.status(RemoteDataSource.APP)
        }

    val statusUpdates: Flow<FeatureFlagRemoteDataStatus>?
        get() {
            return remoteData
                .statusFlow(RemoteDataSource.APP)
                ?.map { it.toFeatureFlagStatus() }
        }

    suspend fun bestEffortRefresh() {
        remoteData.waitForRefresh(RemoteDataSource.APP, MAX_TIMEOUT_MILLIS)
    }

    suspend fun notifyOutOfDate(remoteDataInfo: RemoteDataInfo?) {
        remoteDataInfo?.apply {
            remoteData.notifyOutdated(remoteDataInfo)
        }
    }

    suspend fun fetchFlagRemoteInfo(name: String): RemoteDataFeatureFlagInfo {
        val payloads = remoteData.payloads(PAYLOAD_TYPE)
            .filter { it.remoteDataInfo?.source == RemoteDataSource.APP }

        val flags = payloads
            .asSequence()
            .mapNotNull { it.data.opt(PAYLOAD_TYPE).list?.list }
            .flatten()
            .map { it.optMap() }
            .mapNotNull(FeatureFlagInfo::fromJson)
            .filter { it.name == name }
            .filter { it.timeCriteria?.meets(clock.currentTimeMillis()) ?: true }
            .toList()

        return RemoteDataFeatureFlagInfo(
            flagInfoList = flags,
            remoteDataInfo = payloads.firstOrNull()?.remoteDataInfo
        )
    }
}

internal data class RemoteDataFeatureFlagInfo(
    val flagInfoList: List<FeatureFlagInfo>,
    val remoteDataInfo: RemoteDataInfo?
)

private fun RemoteData.Status.toFeatureFlagStatus(): FeatureFlagRemoteDataStatus {
    return when(this) {
        RemoteData.Status.UP_TO_DATE -> FeatureFlagRemoteDataStatus.UP_TO_DATE
        RemoteData.Status.STALE -> FeatureFlagRemoteDataStatus.STALE
        RemoteData.Status.OUT_OF_DATE -> FeatureFlagRemoteDataStatus.OUT_OF_DATE
    }
}
