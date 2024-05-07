package com.urbanairship.iam.legacy

import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.iam.InAppMessage
import com.urbanairship.push.PushManager

public typealias MessageConvertor = (LegacyInAppMessage) -> AutomationSchedule?
public typealias MessageExtender = (InAppMessage) -> InAppMessage
public typealias ScheduleExtender = (AutomationSchedule) -> AutomationSchedule

/**
 * Legacy in-app messaging protocol
 */
public interface LegacyInAppMessagingInterface {

    /**
     * Optional message converter from a `LegacyInAppMessage` to an `AutomationSchedule`
     */
    public val customMessageConverter: MessageConvertor?

    /**
     * Optional message extender.
     */
    public val messageExtender: MessageExtender?

    /**
     * Optional schedule extender.
     */
    public var scheduleExtender: ScheduleExtender?

    /**
     * Sets whether legacy messages will display immediately upon arrival, instead of waiting
     * until the following foreground. Defaults to `true`.
     */
    public var displayAsapEnabled: Boolean
}

internal interface InternalLegacyInAppMessagingProtocol: LegacyInAppMessagingInterface {
    /*
    func receivedNotificationResponse(_ response: UNNotificationResponse, completionHandler: @escaping () -> Void)

    func receivedRemoteNotification(_ notification: [AnyHashable : Any],
                                    completionHandler: @escaping (UIBackgroundFetchResult) -> Void)
     */
}
//TODO: Implement
public class LegacyInAppMessaging(
    private val pushManager: PushManager
) : LegacyInAppMessagingInterface {

    override val customMessageConverter: MessageConvertor?
        get() = TODO("Not yet implemented")
    override val messageExtender: MessageExtender?
        get() = TODO("Not yet implemented")
    override var scheduleExtender: ScheduleExtender?
        get() = TODO("Not yet implemented")
        set(value) {}
    override var displayAsapEnabled: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
}
