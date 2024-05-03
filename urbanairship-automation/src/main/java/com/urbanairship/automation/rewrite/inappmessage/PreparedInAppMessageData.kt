package com.urbanairship.automation.rewrite.inappmessage

import android.content.Context
import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.rewrite.engine.AutomationPreparerDelegate
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManager
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapterType
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterFactory
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinator
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorManager

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
