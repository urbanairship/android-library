/* Copyright Airship and Contributors */

package com.urbanairship.iam

import android.content.Context
import com.urbanairship.iam.adapter.CustomDisplayAdapter
import com.urbanairship.iam.adapter.CustomDisplayAdapterType
import com.urbanairship.iam.assets.AirshipCachedAssets

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
        factoryBlock: (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?
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
        factoryBlock: (Context, InAppMessage, AirshipCachedAssets) -> CustomDisplayAdapter?
    ) {
        preparer.setAdapterFactoryBlock(type, factoryBlock)
    }

    override fun notifyDisplayConditionsChanged() {
        executor.notifyDisplayConditionsChanged()
    }
}
