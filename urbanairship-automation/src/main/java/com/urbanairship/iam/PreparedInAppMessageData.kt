package com.urbanairship.iam

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.engine.AutomationPreparerDelegate
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.assets.AssetCacheManager
import com.urbanairship.iam.adapter.CustomDisplayAdapter
import com.urbanairship.iam.adapter.CustomDisplayAdapterType
import com.urbanairship.iam.adapter.DisplayAdapter
import com.urbanairship.iam.adapter.DisplayAdapterFactory
import com.urbanairship.iam.coordinator.DisplayCoordinator
import com.urbanairship.iam.coordinator.DisplayCoordinatorManager

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
internal data class PreparedInAppMessageData(
    val message: InAppMessage,
    val displayAdapter: DisplayAdapter,
    val displayCoordinator: DisplayCoordinator
)

internal class InAppMessageAutomationPreparer(
    private val assetsManager: AssetCacheManager,
    private val displayCoordinatorManager: DisplayCoordinatorManager,
    private val displayAdapterFactory: DisplayAdapterFactory
) : AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData> {

    var displayInterval: Long
        get() { synchronized(displayCoordinatorManager) { return displayCoordinatorManager.displayInterval } }
        set(value) { synchronized(displayCoordinatorManager) { displayCoordinatorManager.displayInterval = value} }

    override suspend fun prepare(
        data: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo
    ): Result<PreparedInAppMessageData> {
        val assets = prepareAssets(data, preparedScheduleInfo.scheduleID).getOrElse {
            return Result.failure(it)
        }

        UALog.v { "Making display coordinator ${preparedScheduleInfo.scheduleID}" }
        val coordinator = displayCoordinatorManager.displayCoordinator(data)

        val adapter = displayAdapterFactory.makeAdapter(data, assets).getOrElse {
            return Result.failure(it)
        }

        return Result.success(PreparedInAppMessageData(data, adapter, coordinator))
    }

    override suspend fun cancelled(scheduleID: String) {
        UALog.v { "Execution cancelled $scheduleID" }
        assetsManager.clearCache(scheduleID)
    }

    fun setAdapterFactoryBlock(
        type: CustomDisplayAdapterType,
        factoryBlock: (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?
    ) {
        displayAdapterFactory.setAdapterFactoryBlock(type, factoryBlock)
    }

    private suspend fun prepareAssets(message: InAppMessage, scheduleID: String): Result<AirshipCachedAssets> {
        val imageUrls = message
            .getUrlInfos()
            .mapNotNull {
                return@mapNotNull when(it.type) {
                    UrlInfo.UrlType.IMAGE -> it.url
                    else -> null
                }
            }

        UALog.v { "Preparing assets $scheduleID: $imageUrls" }

        return assetsManager.cacheAsset(scheduleID, imageUrls)
    }
}
