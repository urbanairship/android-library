/* Copyright Airship and Contributors */

package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.annotation.OpenForTesting
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.app.GlobalActivityMonitor
import com.urbanairship.app.SimpleApplicationListener
import com.urbanairship.remotedata.RemoteData
import com.urbanairship.remotedata.RemoteDataInfo
import com.urbanairship.remotedata.RemoteDataPayload
import com.urbanairship.remotedata.RemoteDataSource
import com.urbanairship.util.Network
import com.urbanairship.util.SerialQueue
import java.util.concurrent.atomic.AtomicLong
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
    activityMonitor: ActivityMonitor,
    private val coroutineDispatcher: CoroutineDispatcher
) {
    public constructor(context: Context, remoteData: RemoteData) : this(context, remoteData, Network(), GlobalActivityMonitor.shared(context), AirshipDispatchers.newSerialDispatcher())

    private var sessionNumber: AtomicLong = AtomicLong()
    private var lastRefreshState: MutableMap<RemoteDataSource, Long> = mutableMapOf()
    private var serialQueues: Map<RemoteDataSource, SerialQueue> =
        RemoteDataSource.values().associateWith {
            SerialQueue()
        }

    init {
        activityMonitor.addApplicationListener(object : SimpleApplicationListener() {
            override fun onForeground(time: Long) {
                sessionNumber.incrementAndGet()
            }
        })
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
        val sessionNumber = sessionNumber.get()

        scope.launch {
            refreshOutdated(remoteDataInfo, source, sessionNumber)
            yield()
            onComplete.run()
        }
    }

    public fun refreshAndCheckCurrentSync(remoteDataInfo: RemoteDataInfo?): Boolean {
        val source = remoteDataInfo?.source ?: RemoteDataSource.APP
        val sessionNumber = sessionNumber.get()

        return runBlocking(coroutineDispatcher) {
            bestEffortRefresh(remoteDataInfo, source, sessionNumber)
            isCurrent(remoteDataInfo)
        }
    }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo?): Boolean {
        return remoteDataInfo != null && remoteData.isCurrent(remoteDataInfo)
    }

    private suspend fun refreshOutdated(
        remoteDataInfo: RemoteDataInfo?,
        source: RemoteDataSource,
        sessionNumber: Long
    ) {
        serialQueues[source].run {
            if (!isCurrent(remoteDataInfo)) {
                bestEffortRefresh(remoteDataInfo, source, sessionNumber)
                return
            }

            remoteDataInfo?.let {
                remoteData.notifyOutdated(remoteDataInfo)
            }

            lastRefreshState.remove(source)
            refresh(source, sessionNumber)
        }
    }

    private suspend fun bestEffortRefresh(
        remoteDataInfo: RemoteDataInfo?,
        source: RemoteDataSource,
        sessionNumber: Long
    ) {
        serialQueues[source].run {
            if (remoteDataInfo == null || !isCurrent(remoteDataInfo)) {
                refresh(source, sessionNumber)
            } else if (lastRefreshState[source] != sessionNumber && network.isConnected(context)) {
                refresh(source, sessionNumber)
            }
        }
    }

    private suspend fun refresh(source: RemoteDataSource, sessionNumber: Long) {
        UALog.v { "Attempting to refresh source $source sessionNumber $sessionNumber" }

        if (remoteData.refresh(source)) {
            UALog.v { "Refreshed source $source sessionNumber $sessionNumber" }
            lastRefreshState[source] = sessionNumber
        } else {
            UALog.v { "Refreshed source $source sessionNumber $sessionNumber failed" }
        }
    }
}

public fun interface Cancelable {
    public fun cancel()
}
