package com.urbanairship.automation.rewrite.inappmessage

import com.urbanairship.automation.rewrite.inappmessage.assets.AirshipCachedAssetsInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapterInterface
import com.urbanairship.automation.rewrite.inappmessage.displayadapter.CustomDisplayAdapterType

/**
 * In-app messaging
 */
public interface InAppMessagingInterface {

    /**
     * Display interval
     */
    public var displayInterval: Long

    /**
     * Display interval
     */
    public var displayDelegate: InAppMessageDisplayDelegate?

    /**
     * Sets a factory block for a custom display adapter.
     * If the factory block returns a nil adapter, the default adapter will be used.
     * @param type: The type
     * @param factoryBlock: The factory block
     */
    public fun setAdapterFactoryBlock(
        type: CustomDisplayAdapterType,
        factoryBlock: (InAppMessage, AirshipCachedAssetsInterface) -> CustomDisplayAdapterInterface?
    )

    /**
     * Notifies In-App messages that the display conditions should be reevaluated.
     * This should only be called when state that was used to prevent a display with  `InAppMessageDisplayDelegate` changes.
     */
    public fun notifyDisplayConditionsChanged()

}

internal class InAppMessaging(
    private val executor: InAppMessageAutomationExecutor,
    private val preparer: InAppMessageAutomationPreparer
) : InAppMessagingInterface {

    override var displayInterval: Long
        get() { return preparer.displayInterval }
        set(value) { preparer.displayInterval = value }

    override var displayDelegate: InAppMessageDisplayDelegate?
        get() { return executor.displayDelegate }
        set(value) { executor.displayDelegate = value }

    override fun setAdapterFactoryBlock(
        type: CustomDisplayAdapterType,
        factoryBlock: (InAppMessage, AirshipCachedAssetsInterface) -> CustomDisplayAdapterInterface?
    ) {
        preparer.setAdapterFactoryBlock(type, factoryBlock)
    }

    override fun notifyDisplayConditionsChanged() {
        executor.notifyDisplayConditionsChanged()
    }
}