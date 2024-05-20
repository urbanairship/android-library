package com.urbanairship.iam.legacy

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import com.urbanairship.PreferenceDataStore
import com.urbanairship.UALog
import com.urbanairship.automation.AutomationEngineInterface
import com.urbanairship.automation.AutomationSchedule
import com.urbanairship.automation.AutomationTrigger
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.content.Banner
import com.urbanairship.iam.content.InAppMessageDisplayContent
import com.urbanairship.iam.info.InAppMessageButtonInfo
import com.urbanairship.iam.info.InAppMessageButtonLayoutType
import com.urbanairship.iam.info.InAppMessageColor
import com.urbanairship.iam.info.InAppMessageTextInfo
import com.urbanairship.push.PushManager
import com.urbanairship.util.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

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
    public var customMessageConverter: MessageConvertor?

    /**
     * Optional message extender.
     */
    public var messageExtender: MessageExtender?

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

internal class LegacyInAppMessaging(
    private val context: Context,
    private val pushManager: PushManager,
    private val updates: Flow<LegacyInAppMessageUpdate> = LegacyInAppMessageUpdate.updates(pushManager),
    private val preferenceDataStore: PreferenceDataStore,
    private val automationEngine: AutomationEngineInterface,
    private val legacyAnalytics: LegacyAnalytics,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) : LegacyInAppMessagingInterface {

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    override var customMessageConverter: MessageConvertor? = null

    override var messageExtender: MessageExtender? = null

    override var scheduleExtender: ScheduleExtender? = null

    override var displayAsapEnabled: Boolean = true

    init {
        scope.launch {
            updates.collect {update ->
                when(update) {
                    is LegacyInAppMessageUpdate.DirectOpen -> {
                        processDirectOpen(update.sendId)
                    }

                    is LegacyInAppMessageUpdate.NewMessage -> {
                        processNewMessage(update.message)
                    }
                }
            }
        }
    }

    private suspend fun processNewMessage(message: LegacyInAppMessage) {
        val schedule = createSchedule(message) ?: return

        preferenceDataStore.getString(PENDING_MESSAGE_ID, null)?.let {
            if (automationEngine.getSchedule(it) != null) {
                UALog.d("Pending in-app message replaced.")
                legacyAnalytics.recordReplacedEvent(it, schedule.identifier)
            }

            automationEngine.cancelSchedules(listOf(it))
        }

        // Schedule the new one
        automationEngine.upsertSchedules(listOf(schedule))
        preferenceDataStore.put(PENDING_MESSAGE_ID, schedule.identifier)
    }

    private suspend fun processDirectOpen(sendId: String) {
        if (preferenceDataStore.getString(PENDING_MESSAGE_ID, null) == sendId) {
            preferenceDataStore.remove(PENDING_MESSAGE_ID)

            if (automationEngine.getSchedule(sendId) != null) {
                UALog.d("Pending in-app message cancelled.")
                legacyAnalytics.recordDirectOpenEvent(sendId)
            }

            automationEngine.cancelSchedules(listOf(sendId))
        }
    }

    private fun createSchedule(legacyInAppMessage: LegacyInAppMessage): AutomationSchedule? {
        this.customMessageConverter?.apply {
            return this.invoke(legacyInAppMessage)
        }

        val primaryColor = InAppMessageColor(legacyInAppMessage.primaryColor ?: DEFAULT_PRIMARY_COLOR)
        val secondaryColor = InAppMessageColor(legacyInAppMessage.secondaryColor ?: DEFAULT_SECONDARY_COLOR)

        val buttons: List<InAppMessageButtonInfo>? = legacyInAppMessage.buttonGroupId?.let {
            val group = pushManager.getNotificationActionGroup(it) ?: return@let emptyList()

            group.notificationActionButtons.take(Banner.MAX_BUTTONS).map { button ->
                val drawable: String? = button?.icon?.let { icon ->
                    try {
                        context.resources.getResourceName(icon)
                    } catch (e: Resources.NotFoundException) {
                        UALog.d("Drawable $icon no longer exists or has a new identifier.")
                        null
                    }
                }

                InAppMessageButtonInfo(
                    identifier = button.id,
                    label = InAppMessageTextInfo(
                        text = button.getLabel(context) ?: "",
                        color = primaryColor,
                        alignment = InAppMessageTextInfo.Alignment.CENTER,
                        drawableName = drawable
                    ),
                    actions = legacyInAppMessage.buttonActionValues?.get(button.id),
                    backgroundColor = secondaryColor,
                    borderRadius = DEFAULT_BORDER_RADIUS_DP
                )
            }
        }

        val inAppMessage = InAppMessage(
            name = legacyInAppMessage.id,
            displayContent =  InAppMessageDisplayContent.BannerContent(
                banner = Banner(
                    backgroundColor = primaryColor,
                    dismissButtonColor = secondaryColor,
                    borderRadius = DEFAULT_BORDER_RADIUS_DP,
                    actions = legacyInAppMessage.clickActionValues,
                    durationMs = (legacyInAppMessage.displayDurationMs ?: Banner.DEFAULT_DURATION_MS).coerceAtLeast(
                        MIN_DURATION_MS
                    ),
                    placement = legacyInAppMessage.placement,
                    template = Banner.Template.MEDIA_LEFT,
                    body = InAppMessageTextInfo(legacyInAppMessage.alert ?: "", color = secondaryColor),
                    buttonLayoutType = InAppMessageButtonLayoutType.SEPARATE,
                    buttons = buttons
                )
            ),
            source = InAppMessage.InAppMessageSource.LEGACY_PUSH,
            extras = legacyInAppMessage.extras,
        ).let {
            messageExtender?.invoke(it) ?: it
        }

        val trigger = if (displayAsapEnabled) {
            AutomationTrigger.activeSession(1U)
        } else {
            AutomationTrigger.foreground(1U)
        }

        return AutomationSchedule(
            identifier = legacyInAppMessage.id,
            data = AutomationSchedule.ScheduleData.InAppMessageData(inAppMessage),
            triggers = listOf(trigger),
            endDate = legacyInAppMessage.expiryMs?.toULong() ?: (clock.currentTimeMillis() + DEFAULT_EXPIRY_MS).toULong(),
        ).let {
            scheduleExtender?.invoke(it) ?: it
        }
    }

    internal companion object {
        private const val PENDING_MESSAGE_ID = "com.urbanairship.push.iam.PENDING_MESSAGE_ID"
        internal const val DEFAULT_PRIMARY_COLOR: Int = Color.WHITE
        internal const val DEFAULT_SECONDARY_COLOR: Int = Color.BLACK
        internal const val DEFAULT_BORDER_RADIUS_DP = 2f
        internal const val DEFAULT_EXPIRY_MS = 2592000000L // 30 days
        internal const val MIN_DURATION_MS = 1000L // 1 second
    }
}
