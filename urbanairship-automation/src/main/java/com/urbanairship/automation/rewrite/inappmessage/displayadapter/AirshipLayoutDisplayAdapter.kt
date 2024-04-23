package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import com.urbanairship.UALog
import com.urbanairship.android.layout.util.UrlInfo
import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.banner.BannerAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen.FullScreenAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.html.HtmlDisplayAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout.LayoutAdapter
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.modal.ModalAdapter
import com.urbanairship.automation.rewrite.inappmessage.getUrlInfos
import com.urbanairship.automation.rewrite.utils.NetworkMonitor

internal class AirshipLayoutDisplayAdapter(
    val message: InAppMessage,
    val assets: AirshipCachedAssetsInterface,
    val network: NetworkMonitor,
    val activityMonitor: InAppActivityMonitor
) : DisplayAdapterInterface {

    val contentAdapter: DisplayAdapterInterface

    init {
        when(message.displayContent) {
            is InAppMessageDisplayContent.CustomContent -> {
                throw IllegalArgumentException("Invalid adapter for layout type")
            }
            is InAppMessageDisplayContent.BannerContent -> {
                contentAdapter = BannerAdapter(message.displayContent, assets, activityMonitor)
            }
            is InAppMessageDisplayContent.FullscreenContent -> {
                contentAdapter = FullScreenAdapter(message.displayContent, assets, activityMonitor)
            }
            is InAppMessageDisplayContent.HTMLContent -> {
                contentAdapter = HtmlDisplayAdapter(
                    displayContent = message.displayContent,
                    assets = assets,
                    messageExtras = message.extras,
                    activityMonitor = activityMonitor)
            }
            is InAppMessageDisplayContent.ModalContent -> {
                contentAdapter = ModalAdapter(message.displayContent, assets, activityMonitor)
            }
            is InAppMessageDisplayContent.AirshipLayoutContent -> {
                contentAdapter = LayoutAdapter(
                    displayContent = message.displayContent,
                    assets = assets,
                    messageExtras = message.extras,
                    activityMonitor = activityMonitor)
            }
        }
    }

    override fun getIsReady(): Boolean {
        if (activityMonitor.resumedActivities.isEmpty()) {
            return false
        }

        if (!contentAdapter.getIsReady()) {
            return false
        }

        val needsNetwork = message.getUrlInfos().any {
            when(it.type) {
                UrlInfo.UrlType.WEB_PAGE -> it.requiresNetwork
                UrlInfo.UrlType.IMAGE -> it.requiresNetwork && !assets.isCached(it.url)
                UrlInfo.UrlType.VIDEO -> it.requiresNetwork
            }
        }

        return !needsNetwork || network.isConnected
    }

    override suspend fun waitForReady() {
        if (getIsReady()) { return }

        for (connected in network.updates) {
            if (connected) { break }
        }

        contentAdapter.waitForReady()
    }

    override suspend fun display(
        context: Context, analytics: InAppMessageAnalyticsInterface
    ): DisplayResult {
        return when(message.displayContent) {
            is InAppMessageDisplayContent.CustomContent -> {
                UALog.e { "Custom content in airship layout display. ignoring" }
                DisplayResult.FINISHED
            }
            else -> contentAdapter.display(context, analytics)
        }
    }
}
