/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.content.Context
import com.urbanairship.app.ActivityMonitor
import com.urbanairship.automation.utils.NetworkMonitor
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.InAppMessageContentExtender
import com.urbanairship.iam.actions.InAppActionRunner
import com.urbanairship.iam.adapter.banner.BannerDisplayDelegate
import com.urbanairship.iam.adapter.fullscreen.FullscreenDisplayDelegate
import com.urbanairship.iam.adapter.html.HtmlDisplayDelegate
import com.urbanairship.iam.adapter.layout.AirshipLayoutDisplayDelegate
import com.urbanairship.iam.adapter.modal.ModalDisplayDelegate
import com.urbanairship.iam.assets.AirshipCachedAssets
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.content.InAppMessageDisplayContent.BannerContent
import com.urbanairship.iam.content.InAppMessageDisplayContent.FullscreenContent
import com.urbanairship.iam.content.InAppMessageDisplayContent.HTMLContent
import com.urbanairship.iam.content.InAppMessageDisplayContent.ModalContent

private typealias AdapterBuilder = (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?

internal class DisplayAdapterFactory(
    private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val activityMonitor: ActivityMonitor
) {
    private val customAdapters = mutableMapOf<CustomDisplayAdapterType, AdapterBuilder>()
    var messageContentExtender: InAppMessageContentExtender? = null

    private fun makeCustomAdapter(context: Context, message: InAppMessage, assets: AirshipCachedAssets): DisplayAdapter? {
        val type = when(message.displayContent) {
            is BannerContent -> CustomDisplayAdapterType.BANNER
            is FullscreenContent -> CustomDisplayAdapterType.FULLSCREEN
            is HTMLContent -> CustomDisplayAdapterType.HTML
            is ModalContent -> CustomDisplayAdapterType.MODAL
            is InAppMessageDisplayContent.CustomContent -> CustomDisplayAdapterType.CUSTOM
            is InAppMessageDisplayContent.AirshipLayoutContent ->  null
        } ?: return null

        val factory = synchronized(customAdapters) {
            customAdapters[type]
        }

        val custom = factory?.invoke(context, message, assets) ?: return null
        return CustomDisplayAdapterWrapper(custom)
    }

    private fun makeDefaultAdapter(message: InAppMessage, assets: AirshipCachedAssets, actionRunner: InAppActionRunner): DisplayAdapter? {
        val extendedMessage = extendMessage(message)
        val delegate = when(val extendedContent = extendedMessage.displayContent) {
            is BannerContent -> BannerDisplayDelegate(
                displayContent = extendedContent,
                assets = assets,
                activityMonitor = activityMonitor,
                actionRunner = actionRunner
            )
            is FullscreenContent -> FullscreenDisplayDelegate(
                displayContent = extendedContent,
                assets = assets,
                activityMonitor = activityMonitor,
                actionRunner = actionRunner
            )
            is HTMLContent -> HtmlDisplayDelegate(
                displayContent = extendedContent,
                assets = assets,
                messageExtras = extendedMessage.extras,
                activityMonitor = activityMonitor,
                actionRunner = actionRunner
            )
            is ModalContent -> ModalDisplayDelegate(
                displayContent = extendedContent,
                assets = assets,
                activityMonitor = activityMonitor,
                actionRunner = actionRunner
            )
            is InAppMessageDisplayContent.AirshipLayoutContent -> AirshipLayoutDisplayDelegate(
                displayContent = extendedContent,
                assets = assets,
                messageExtras = extendedMessage.extras,
                activityMonitor = activityMonitor,
                actionRunner = actionRunner
            )
            else -> null
        } ?: return null

        return DelegatingDisplayAdapter(extendedMessage, assets, delegate, networkMonitor, activityMonitor)
    }

    fun setAdapterFactoryBlock(type: CustomDisplayAdapterType, factoryBlock: AdapterBuilder) {
        synchronized(customAdapters) {
            customAdapters[type] = factoryBlock
        }
    }

    fun makeAdapter(
        message: InAppMessage,
        assets: AirshipCachedAssets,
        actionRunner: InAppActionRunner
    ): Result<DisplayAdapter> {
        val adapter = makeCustomAdapter(context, message, assets) ?: makeDefaultAdapter(message, assets, actionRunner)
        return if (adapter != null) {
            Result.success(adapter)
        } else {
            Result.failure(IllegalArgumentException("No display adapter for message $message"))
        }
    }

    private fun extendMessage(message: InAppMessage): InAppMessage {
        val extendedContent = messageContentExtender?.extend(message) ?: message.displayContent
        return message.newBuilder()
            .setDisplayContent(extendedContent)
            .build()
    }
}
