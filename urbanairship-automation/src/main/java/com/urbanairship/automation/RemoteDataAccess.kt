package com.urbanairship.automation

import android.content.Context
import androidx.annotation.RestrictTo
import androidx.core.util.Consumer
import com.urbanairship.AirshipDispatchers
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
import kotlinx.coroutines.yield

@OpenForTesting
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataAccess internal constructor(
    private val remoteData: RemoteData,
    private val network: Network,
    private val coroutineDispatcher: CoroutineDispatcher
) {
    public constructor(remoteData: RemoteData) : this(remoteData, Network(), AirshipDispatchers.newSerialDispatcher())

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
        remoteDataInfo?.let { remoteData.notifyOutdated(remoteDataInfo) }
        scope.launch {
            remoteData.refresh(remoteDataInfo?.source ?: RemoteDataSource.APP)
            yield()
            onComplete.run()
        }
    }

    public fun refreshAndCheckCurrentSync(context: Context, remoteDataInfo: RemoteDataInfo?): Boolean {
        return runBlocking(coroutineDispatcher) {
            if (network.isConnected(context)) {
                remoteData.refresh(remoteDataInfo?.source ?: RemoteDataSource.APP)
            }

            if (remoteDataInfo == null) {
                false
            } else {
                remoteData.isCurrent(remoteDataInfo)
            }
        }
    }

    public fun isCurrent(remoteDataInfo: RemoteDataInfo): Boolean {
        return remoteData.isCurrent(remoteDataInfo)
    }
}

public fun interface Cancelable {
    public fun cancel()
}
