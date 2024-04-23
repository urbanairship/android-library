package com.urbanairship.automation.rewrite.inappmessage

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.rewrite.engine.AutomationPreparerDelegate
import com.urbanairship.automation.rewrite.engine.PreparedScheduleInfo
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AssetCacheManagerInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapterType
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterFactory
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterFactoryInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.DisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorInterface
import com.urbanairship.automation.rewrite.inappmessage.displaycoordinator.DisplayCoordinatorManagerInterface

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class PreparedInAppMessageData(
    val message: InAppMessage,
    val displayAdapter: DisplayAdapterInterface,
    val displayCoordinator: DisplayCoordinatorInterface
)

internal class InAppMessageAutomationPreparer(
    private val activityMonitor: InAppActivityMonitor,
    private val assetsManager: AssetCacheManagerInterface,
    private val displayCoordinatorManager: DisplayCoordinatorManagerInterface,
    private val displayAdapterFactory: DisplayAdapterFactoryInterface = DisplayAdapterFactory()
) : AutomationPreparerDelegate<InAppMessage, PreparedInAppMessageData> {

    var displayInterval: Long
        get() { synchronized(displayCoordinatorManager) { return displayCoordinatorManager.displayInterval } }
        set(value) { synchronized(displayCoordinatorManager) { displayCoordinatorManager.displayInterval = value} }

    @Throws(IllegalArgumentException::class)
    override suspend fun prepare(
        data: InAppMessage,
        preparedScheduleInfo: PreparedScheduleInfo
    ): PreparedInAppMessageData {
        val assets = prepareAssets(data, preparedScheduleInfo.scheduleID)
            ?: throw IllegalArgumentException("Failed to cache assets")

        UALog.v { "Making display coordinator ${preparedScheduleInfo.scheduleID}" }
        val coordinator = displayCoordinatorManager.displayCoordinator(data)
        val adapter = displayAdapterFactory.makeAdapter(data, assets, activityMonitor)

        return PreparedInAppMessageData(data, adapter, coordinator)
    }

    override suspend fun cancelled(scheduleID: String) {
        UALog.v { "Execution cancelled $scheduleID" }
        assetsManager.clearCache(scheduleID)
    }

    fun setAdapterFactoryBlock(
        type: CustomDisplayAdapterType,
        factoryBlock: (InAppMessage, AirshipCachedAssetsInterface) -> CustomDisplayAdapterInterface?
    ) {
        displayAdapterFactory.setAdapterFactoryBlock(type, factoryBlock)
    }

    private suspend fun prepareAssets(message: InAppMessage, scheduleID: String): AirshipCachedAssetsInterface? {
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
