/* Copyright Airship and Contributors */

package com.urbanairship.iam

import android.content.Context
import androidx.annotation.MainThread
import com.urbanairship.android.layout.assets.AirshipCachedAssets
import com.urbanairship.iam.adapter.CustomDisplayAdapter
import com.urbanairship.iam.adapter.CustomDisplayAdapterType
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * In-app messaging
 */
public interface InAppMessagingInterface {

    /**
     * The interval to wait between displaying in-app messages.
     */
    @get:JvmSynthetic
    @set:JvmSynthetic
    public var displayInterval: Duration

    /**
     * The interval to wait between displaying in-app messages, in whole seconds.
     *
     * Provided for Java callers, which cannot express a [Duration]. [displayInterval] is the
     * source of truth; this reads and writes through to it.
     *
     * Counterparts elsewhere in the SDK are named `…Ms`; this one is in seconds because the
     * `Long` property it replaces was, so an existing Java call keeps its meaning. Reading it
     * truncates a [displayInterval] with sub-second precision.
     */
    public var displayIntervalSeconds: Long
        get() = displayInterval.inWholeSeconds
        set(value) { displayInterval = value.seconds }

    /**
     * Delegate consulted before a message is displayed, to allow the app to block or defer it.
     */
    public var displayDelegate: InAppMessageDisplayDelegate?

    /**
     * Message extender
     *
     * The extender is called before the message is displayed and allows the message to be modified.
     */
    public var messageContentExtender: InAppMessageContentExtender?

    /**
     * Called during schedule preparation to allow app-side logic to suppress the message before
     * assets are fetched. Throwing causes the prepare operation to retry with backoff — catch
     * internally to fail open instead.
     */
    @get:MainThread
    @set:MainThread
    public var onCheckSuppression: (suspend (InAppMessage, String) -> SuppressionResult)?

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

    override var displayInterval: Duration
        get() { return preparer.displayInterval }
        set(value) { preparer.displayInterval = value }

    override var displayDelegate: InAppMessageDisplayDelegate?
        get() { return executor.displayDelegate }
        set(value) { executor.displayDelegate = value }

    override var messageContentExtender: InAppMessageContentExtender?
        get() { return preparer.messageContentExtender }
        set(value) { preparer.messageContentExtender = value }

    override var onCheckSuppression: (suspend (InAppMessage, String) -> SuppressionResult)?
        get() = preparer.onCheckSuppression
        set(value) { preparer.onCheckSuppression = value }

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
