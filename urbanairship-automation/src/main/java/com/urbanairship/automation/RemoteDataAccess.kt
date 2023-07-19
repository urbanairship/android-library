/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Network
import com.urbanairship.util.SerialQueue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataAccess internal constructor(
    private val context: Context,
    private val remoteData: RemoteData,
    private val network: Network,
    private val coroutineDispatcher: CoroutineDispatcher
) {
    public constructor(context: Context, remoteData: RemoteData) : this(context, remoteData, Network(), AirshipDispatchers.newSerialDispatcher())

    private var lastRefreshState: MutableMap<RemoteDataSource, Long> = mutableMapOf()
    private var serialQueues: Map<RemoteDataSource, SerialQueue> =
        RemoteDataSource.values().associateWith {
            SerialQueue()
        }

    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    public fun subscribe(onUpdate: Consumer<List<RemoteDataPayload>>): Cancelable {
        val job = scope.launch {
            remoteData.payloadFlow("in_app_messages").collect {
                onUpdate.accept(it)
            }
        }

        return Cancelable { job.cancel() }
    }

    public fun refreshOutdated(remoteDataInfo: RemoteDataInfo?, onComplete: Runnable) {
        UALog.v { "Refreshing outdated remoteDataInfo $remoteDataInfo" }

        val source = remoteDataInfo?.source ?: RemoteDataSource.APP

        scope.launch {
            refreshOutdated(remoteDataInfo, source)
            yield()
            onComplete.run()
        }
    }

    public fun refreshAndCheckCurrentSync(remoteDataInfo: RemoteDataInfo?): Boolean {
        val source = remoteDataInfo?.source ?: RemoteDataSource.APP

        return runBlocking(coroutineDispatcher) {
            bestEffortRefresh(remoteDataInfo, source)
            isCurrent(remoteDataInfo)
        }
    }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo?): Boolean {
        return remoteDataInfo != null && remoteData.isCurrent(remoteDataInfo)
    }

    private suspend fun refreshOutdated(
        remoteDataInfo: RemoteDataInfo?,
        source: RemoteDataSource
    ) {
        serialQueues[source].run {
            if (!isCurrent(remoteDataInfo)) {
                bestEffortRefresh(remoteDataInfo, source)
                return
            }

            remoteDataInfo?.let {
                remoteData.notifyOutdated(remoteDataInfo)
            }

            lastRefreshState.remove(source)
            refresh(source)
        }
    }

    private suspend fun bestEffortRefresh(
        remoteDataInfo: RemoteDataInfo?,
        source: RemoteDataSource
    ) {
        serialQueues[source].run {
            if (isCurrent(remoteDataInfo)) {
                if (remoteData.status(source) != RemoteData.Status.UP_TO_DATE &&
                    network.isConnected(context)) {
                    refresh(source)
                }
            } else {
                refresh(source)
            }
        }
    }

    private suspend fun refresh(source: RemoteDataSource) {
        UALog.v { "Attempting to refresh source $source" }

        if (remoteData.refresh(source)) {
            UALog.v { "Refreshed source $source" }
        } else {
            UALog.v { "Refreshed source $source failed" }
        }
    }
}

public fun interface Cancelable {
    public fun cancel()
}
