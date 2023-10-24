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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataAccess internal constructor(
    private val context: Context,
    private val remoteData: RemoteData,
    private val network: Network,
    private val coroutineDispatcher: CoroutineDispatcher
) {
    public constructor(context: Context, remoteData: RemoteData) : this(context, remoteData, Network(), AirshipDispatchers.newSerialDispatcher())

    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob())

    public fun subscribe(onUpdate: Consumer<List<RemoteDataPayload>>): Cancelable {
        val job = scope.launch {
            remoteData.payloadFlow("in_app_messages").collect {
                onUpdate.accept(it)
            }
        }

        return Cancelable { job.cancel() }
    }

    public fun requiresRefresh(remoteDatInfo: RemoteDataInfo?): Boolean {
        if (!isCurrent(remoteDatInfo)) {
            return true
        }
        val source = remoteDatInfo?.source ?: RemoteDataSource.APP
        return when (remoteData.status(source)) {
            RemoteData.Status.UP_TO_DATE -> false
            RemoteData.Status.STALE -> false
            RemoteData.Status.OUT_OF_DATE -> true
        }
    }

    public fun waitFullRefresh(remoteDataInfo: RemoteDataInfo?, runnable: Runnable) {
        val source = remoteDataInfo?.source ?: RemoteDataSource.APP
        scope.launch {
            remoteData.waitForRefresh(source)
            runnable.run()
        }
    }

    public fun notifyOutdated(remoteDataInfo: RemoteDataInfo?) {
        UALog.v { "Refreshing outdated remoteDataInfo $remoteDataInfo" }
        if (remoteDataInfo == null) {
            return
        }

        runBlocking(coroutineDispatcher) {
            remoteData.notifyOutdated(remoteDataInfo)
        }
    }

    public fun bestEffortRefresh(remoteDataInfo: RemoteDataInfo?): Boolean {
        val source = remoteDataInfo?.source ?: RemoteDataSource.APP

        return runBlocking(coroutineDispatcher) {
            if (!isCurrent(remoteDataInfo)) {
                return@runBlocking false
            }

            // No need to wait for update if we are current and status is up to date
            if (remoteData.status(source) == RemoteData.Status.UP_TO_DATE) {
                return@runBlocking true
            }

            // if we are connected wait for refresh
            if (network.isConnected(context)) {
                remoteData.waitForRefreshAttempt(source)
            }

            isCurrent(remoteDataInfo)
        }
    }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo?): Boolean {
        return remoteDataInfo != null && remoteData.isCurrent(remoteDataInfo)
    }
}

public fun interface Cancelable {
    public fun cancel()
}
