package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssets
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.banner.BannerDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.fullscreen.FullscreenDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.html.HtmlDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.layout.AirshipLayoutDisplayDelegate
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.modal.ModalDisplayDelegate
import com.urbanairship.automation.rewrite.utils.NetworkMonitor

private typealias AdapterBuilder = (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?


internal class DisplayAdapterFactory(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val activityMonitor: ActivityMonitor
) {
    private val customAdapters = mutableMapOf<CustomDisplayAdapterType, AdapterBuilder>()

    private fun makeCustomAdapter(context: Context,  message: InAppMessage, assets: AirshipCachedAssets): DisplayAdapter? {
        val type = when(message.displayContent) {
            is InAppMessageDisplayContent.BannerContent -> CustomDisplayAdapterType.BANNER
            is InAppMessageDisplayContent.FullscreenContent -> CustomDisplayAdapterType.FULLSCREEN
            is InAppMessageDisplayContent.HTMLContent -> CustomDisplayAdapterType.HTML
            is InAppMessageDisplayContent.ModalContent ->CustomDisplayAdapterType.MODAL
            is InAppMessageDisplayContent.CustomContent -> CustomDisplayAdapterType.CUSTOM
            is InAppMessageDisplayContent.AirshipLayoutContent ->  null
        } ?: return null

        val factory = synchronized(customAdapters) {
            customAdapters[type]
        }

        val custom = factory?.invoke(context, message, assets) ?: return null
        return CustomDisplayAdapterWrapper(custom)
    }

    private fun makeDefaultAdapter(message: InAppMessage, assets: AirshipCachedAssets): DisplayAdapter? {
        val delegate = when(message.displayContent) {
            is InAppMessageDisplayContent.BannerContent -> BannerDisplayDelegate(message.displayContent, assets, activityMonitor)
            is InAppMessageDisplayContent.FullscreenContent -> FullscreenDisplayDelegate(message.displayContent, assets, activityMonitor)
            is InAppMessageDisplayContent.HTMLContent -> HtmlDisplayDelegate(message.displayContent, assets, message.extras, activityMonitor)
            is InAppMessageDisplayContent.ModalContent -> ModalDisplayDelegate(message.displayContent, assets, activityMonitor)
            is InAppMessageDisplayContent.AirshipLayoutContent -> AirshipLayoutDisplayDelegate(message.displayContent, assets, message.extras, activityMonitor)
            else -> null
        } ?: return null

        return DelegatingDisplayAdapter(message, assets, delegate, networkMonitor, activityMonitor)
    }

    fun setAdapterFactoryBlock(type: CustomDisplayAdapterType, factoryBlock: AdapterBuilder) {
        synchronized(customAdapters) {
            customAdapters[type] = factoryBlock
        }
    }

    fun makeAdapter(
        message: InAppMessage,
        assets: AirshipCachedAssets,
    ): Result<DisplayAdapter> {
        val adapter = makeCustomAdapter(context, message, assets) ?: makeDefaultAdapter(message, assets)
        return if (adapter != null) {
            Result.success(adapter)
        } else {
            Result.failure(IllegalArgumentException("No display adapter for message $message"))
        }
    }
}
