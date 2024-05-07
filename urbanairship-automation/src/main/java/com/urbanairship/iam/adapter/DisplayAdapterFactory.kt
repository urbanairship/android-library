package com.urbanairship.iam.adapter

import android.content.Context
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.adapter.banner.BannerDisplayDelegate
import com.urbanairship.iam.adapter.fullscreen.FullscreenDisplayDelegate
import com.urbanairship.iam.adapter.html.HtmlDisplayDelegate
import com.urbanairship.iam.adapter.layout.AirshipLayoutDisplayDelegate
import com.urbanairship.iam.adapter.modal.ModalDisplayDelegate
import com.urbanairship.automation.utils.NetworkMonitor

private typealias AdapterBuilder = (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?


internal class DisplayAdapterFactory(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val activityMonitor: ActivityMonitor
) {
    private val customAdapters = mutableMapOf<CustomDisplayAdapterType, AdapterBuilder>()

    private fun makeCustomAdapter(context: Context, message: InAppMessage, assets: AirshipCachedAssets): DisplayAdapter? {
        val type = when(message.displayContent) {
            is InAppMessageDisplayContent.BannerContent -> CustomDisplayAdapterType.BANNER
            is InAppMessageDisplayContent.FullscreenContent -> CustomDisplayAdapterType.FULLSCREEN
            is InAppMessageDisplayContent.HTMLContent -> CustomDisplayAdapterType.HTML
            is InAppMessageDisplayContent.ModalContent -> CustomDisplayAdapterType.MODAL
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
