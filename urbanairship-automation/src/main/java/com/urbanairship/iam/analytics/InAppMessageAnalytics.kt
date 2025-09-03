/* Copyright Airship and Contributors */

package com.urbanairship.iam.analytics

import com.urbanairship.AirshipDispatchers
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.automation.engine.PreparedScheduleInfo
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.events.InAppDisplayEvent
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.json.JsonValue
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import com.urbanairship.util.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface InAppMessageAnalyticsInterface {
    fun recordEvent(event: InAppEvent, layoutContext: LayoutData?)
    fun customEventContext(state: LayoutData?): InAppCustomEventContext
}

internal class InAppMessageAnalytics private constructor(
    private val preparedScheduleInfo: PreparedScheduleInfo,
    private val messageId: InAppEventMessageId,
    private val source: InAppEventSource,
    private val renderedLocale: JsonValue?,
    private val eventRecorder: InAppEventRecorderInterface,
    private val isReportingEnabled: Boolean,
    private val historyStore: MessageDisplayHistoryStoreInterface,
    private val displayImpressionRule: InAppDisplayImpressionRule,
    private var _displayHistory: MutableStateFlow<MessageDisplayHistory>,
    private var _displayContext: MutableStateFlow<InAppEventContext.Display>,
    private val clock: Clock,
    dispatcher: CoroutineDispatcher
): InAppMessageAnalyticsInterface {

    constructor(preparedScheduleInfo: PreparedScheduleInfo,
                message: InAppMessage,
                eventRecorder: InAppEventRecorderInterface,
                historyStore: MessageDisplayHistoryStoreInterface,
                displayImpressionRule: InAppDisplayImpressionRule,
                displayHistory: MessageDisplayHistory,
                clock: Clock = Clock.DEFAULT_CLOCK,
                dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()) :
            this(
                preparedScheduleInfo = preparedScheduleInfo,
                messageId = makeMessageId(message, preparedScheduleInfo.scheduleId, preparedScheduleInfo.campaigns),
                source = makeEventSource(message),
                renderedLocale = message.renderedLocale,
                eventRecorder = eventRecorder,
                isReportingEnabled = message.isReportingEnabled ?: true,
                historyStore = historyStore,
                displayImpressionRule = displayImpressionRule,
                _displayHistory = MutableStateFlow(displayHistory),
                _displayContext = MutableStateFlow(
                    InAppEventContext.Display(
                        triggerSessionId = preparedScheduleInfo.triggerSessionId,
                        isFirstDisplay = displayHistory.lastDisplay == null,
                        isFirstDisplayTriggerSessionId = preparedScheduleInfo.triggerSessionId != displayHistory.lastDisplay?.triggerSessionId
                    )
                ),
                clock = clock,
                dispatcher = dispatcher
            )

    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    private val displayHistory: StateFlow<MessageDisplayHistory> = _displayHistory.asStateFlow()
    private val displayContext: StateFlow<InAppEventContext.Display> = _displayContext.asStateFlow()

    private companion object {
        fun makeMessageId(message: InAppMessage, scheduleID: String, campaigns: JsonValue?): InAppEventMessageId {
            return when(message.source ?:  InAppMessage.Source.REMOTE_DATA) {
                 InAppMessage.Source.REMOTE_DATA -> InAppEventMessageId.AirshipId(
                    scheduleID,
                    campaigns
                )
                 InAppMessage.Source.APP_DEFINED -> InAppEventMessageId.AppDefined(
                    scheduleID
                )
                 InAppMessage.Source.LEGACY_PUSH -> InAppEventMessageId.Legacy(scheduleID)
            }
        }

        fun makeEventSource(message: InAppMessage): InAppEventSource {
             return when(message.source ?:  InAppMessage.Source.REMOTE_DATA) {
                 InAppMessage.Source.APP_DEFINED -> InAppEventSource.APP_DEFINED
                else -> InAppEventSource.AIRSHIP
            }
        }
    }

    override fun recordEvent(event: InAppEvent, layoutContext: LayoutData?) {
        val now = clock.currentTimeMillis()

        if (event is InAppDisplayEvent) {
            val lastDisplay = displayHistory.value.lastDisplay
            lastDisplay?.let {
                if (preparedScheduleInfo.triggerSessionId == it.triggerSessionId) {
                    _displayContext.update { value ->
                        value.isFirstDisplay = false
                        value.isFirstDisplayTriggerSessionId = false
                        value
                    }
                } else {
                    _displayContext.update { value ->
                        value.isFirstDisplay = false
                        value
                    }
                }
            }

            if (recordImpression(now)) {
                _displayHistory.update { value ->
                    MessageDisplayHistory(
                        lastImpression = MessageDisplayHistory.LastImpression(
                            now,
                            preparedScheduleInfo.triggerSessionId
                        ),
                        lastDisplay = value.lastDisplay
                    )
                }
            }

            _displayHistory.update { value ->
                MessageDisplayHistory(
                    lastImpression = value.lastImpression,
                    lastDisplay = MessageDisplayHistory.LastDisplay(
                        preparedScheduleInfo.triggerSessionId
                    )
                )
            }

            scope.launch {
                historyStore.set(displayHistory.value, preparedScheduleInfo.scheduleId)
            }
        }

        if (!isReportingEnabled) {
            return
        }

        val data = InAppEventData(
            event = event,
            context = InAppEventContext.makeContext(
                reportingContext = preparedScheduleInfo.reportingContext,
                experimentResult = preparedScheduleInfo.experimentResult,
                layoutContext = layoutContext,
                displayContext = displayContext.value
            ),
            source = source,
            messageId = messageId,
            renderedLocale = renderedLocale
        )

        eventRecorder.recordEvent(data)
    }

    override fun customEventContext(state: LayoutData?): InAppCustomEventContext {
        return InAppCustomEventContext(
            id = messageId,
            context = InAppEventContext.makeContext(
                reportingContext = preparedScheduleInfo.reportingContext,
                experimentResult = preparedScheduleInfo.experimentResult,
                layoutContext = state,
                displayContext = displayContext.value
            )
        )
    }

    private fun shouldRecordImpression(): Boolean {
        val lastImpression = displayHistory.value.lastImpression ?: return true

        if (preparedScheduleInfo.triggerSessionId != lastImpression.triggerSessionId) {
             return true
        }

        return when (displayImpressionRule) {
            is InAppDisplayImpressionRule.Interval -> {
                (clock.currentTimeMillis() - lastImpression.date) >= displayImpressionRule.value.inWholeMilliseconds
            }
            is InAppDisplayImpressionRule.Once -> false
        }
    }

    private fun recordImpression(date: Long): Boolean {
        if (!shouldRecordImpression()) {
            return false
        }

        val productId = preparedScheduleInfo.productId ?: return false

        val event = MeteredUsageEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityId = messageId.identifier,
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = productId,
            reportingContext = preparedScheduleInfo.reportingContext,
            timestamp = date,
            contactId = preparedScheduleInfo.contactId
        )

        eventRecorder.recordImpressionEvent(event)

        return true
    }
}
