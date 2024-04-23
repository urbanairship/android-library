package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import com.urbanairship.automation.rewrite.inappmessage.InAppActivityMonitor
import com.urbanairship.automation.rewrite.inappmessage.InAppMessage
import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.content.InAppMessageDisplayContent
import com.urbanairship.automation.rewrite.utils.NetworkMonitor

private typealias AdapterBuilder = (InAppMessage, AirshipCachedAssetsInterface) -> CustomDisplayAdapterInterface?

internal interface DisplayAdapterFactoryInterface {
    fun setAdapterFactoryBlock(
        forType: CustomDisplayAdapterType,
        factoryBlock: AdapterBuilder
    )

    @Throws(IllegalArgumentException::class)
    fun makeAdapter(
        message: InAppMessage,
        assets: AirshipCachedAssetsInterface,
        activityMonitor: InAppActivityMonitor
    ): DisplayAdapterInterface
}

internal class DisplayAdapterFactory(
    private val networkMonitor: NetworkMonitor = NetworkMonitor.shared()
) : DisplayAdapterFactoryInterface {
    private val customAdapters = mutableMapOf<CustomDisplayAdapterType, AdapterBuilder>()

    private fun getBuilder(type: CustomDisplayAdapterType): AdapterBuilder? {
        return synchronized(customAdapters) { customAdapters[type] }
    }

    private fun setBuilder(type: CustomDisplayAdapterType, builder: AdapterBuilder) {
        synchronized(customAdapters) {
            customAdapters[type] = builder
        }
    }

    override fun setAdapterFactoryBlock(
        forType: CustomDisplayAdapterType, factoryBlock: AdapterBuilder
    ) {
        setBuilder(forType, factoryBlock)
    }

    @Throws(IllegalArgumentException::class)
    override fun makeAdapter(
        message: InAppMessage,
        assets: AirshipCachedAssetsInterface,
        activityMonitor: InAppActivityMonitor
    ): DisplayAdapterInterface {
        val factoryBlock: AdapterBuilder?

        when(message.displayContent) {
            is InAppMessageDisplayContent.BannerContent -> {
                factoryBlock = getBuilder(CustomDisplayAdapterType.BANNER)
            }
            is InAppMessageDisplayContent.FullscreenContent -> {
                factoryBlock = getBuilder(CustomDisplayAdapterType.FULLSCREEN)
            }
            is InAppMessageDisplayContent.HTMLContent -> {
                factoryBlock = getBuilder(CustomDisplayAdapterType.HTML)
            }
            is InAppMessageDisplayContent.ModalContent -> {
                factoryBlock = getBuilder(CustomDisplayAdapterType.MODAL)
            }
            is InAppMessageDisplayContent.CustomContent -> {
                factoryBlock = getBuilder(CustomDisplayAdapterType.CUSTOM)
                if (factoryBlock == null) {
                    throw IllegalArgumentException("No adapter for message $message")
                }
            }
            is InAppMessageDisplayContent.AirshipLayoutContent -> factoryBlock = null
        }

        val custom = factoryBlock?.invoke(message, assets)
        return if (custom != null) {
            CustomDisplayAdapterWrapper(custom)
        } else {
            AirshipLayoutDisplayAdapter(message, assets, networkMonitor, activityMonitor)
        }
    }
}
