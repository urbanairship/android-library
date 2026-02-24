package com.urbanairship.messagecenter

import com.urbanairship.AirshipDispatchers
import com.urbanairship.android.layout.analytics.LayoutEventContext
import com.urbanairship.android.layout.analytics.LayoutEventData
import com.urbanairship.android.layout.analytics.LayoutEventMessageId
import com.urbanairship.android.layout.analytics.LayoutEventRecorderInterface
import com.urbanairship.android.layout.analytics.LayoutEventSource
import com.urbanairship.android.layout.analytics.LayoutMessageAnalyticsInterface
import com.urbanairship.android.layout.analytics.MessageDisplayHistory
import com.urbanairship.android.layout.analytics.MessageDisplayHistoryStoreInterface
import com.urbanairship.android.layout.analytics.events.InAppDisplayEvent
import com.urbanairship.android.layout.analytics.events.LayoutEvent
import com.urbanairship.android.layout.analytics.makeContext
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.json.JsonValue
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import com.urbanairship.util.Clock
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class MessageAnalytics(
    private val messageId: LayoutEventMessageId,
    private val productId: String,
    private val reportingContext: JsonValue?,
    private val eventRecorder: LayoutEventRecorderInterface,
    private val eventSource: LayoutEventSource,
    private val displayHistoryStore: MessageDisplayHistoryStoreInterface,
    dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sessionId: String = UUID.randomUUID().toString()
): LayoutMessageAnalyticsInterface {

    constructor(
        message: Message,
        eventRecorder: LayoutEventRecorderInterface,
        displayHistoryStore: MessageDisplayHistoryStoreInterface,
        dispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher(),
        clock: Clock = Clock.DEFAULT_CLOCK
    ): this(
        messageId = LayoutEventMessageId.AirshipId(
            identifier = message.id,
            campaigns = null
        ),
        productId = message.productId ?: DEFAULT_PRODUCT_ID,
        reportingContext = message.reporting,
        eventRecorder = eventRecorder,
        displayHistoryStore = displayHistoryStore,
        eventSource = LayoutEventSource.AIRSHIP,
        dispatcher = dispatcher,
        clock = clock
    )

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val displayContext = MutableStateFlow<LayoutEventContext.Display?>(null) //TODO: make sure the value is updated
    private val historyState = MutableStateFlow<MessageDisplayHistory?>(null)

    init {
        scope.launch {
            val history = displayHistoryStore.get(messageId.identifier)
            historyState.value = history

            val lastTriggerSession = history.lastDisplay?.triggerSessionId
            displayContext.value = LayoutEventContext.Display(
                triggerSessionId = lastTriggerSession ?: sessionId,
                isFirstDisplay = history.lastDisplay == null,
                isFirstDisplayTriggerSessionId = lastTriggerSession == history.lastImpression?.triggerSessionId
            )
        }
    }

    override fun recordEvent(
        event: LayoutEvent, layoutContext: LayoutData?
    ) {
        scope.launch {
            val now = clock.currentTimeMillis()

            if (event is InAppDisplayEvent) {
                var history = displayHistoryStore.get(messageId.identifier)
                history.lastDisplay?.let { display ->
                    if (display.triggerSessionId != sessionId) {
                        displayContext.update { value ->
                            value?.isFirstDisplay = false
                            value?.isFirstDisplayTriggerSessionId = false
                            value
                        }
                    } else {
                        displayContext.update { value ->
                            value?.isFirstDisplay = false
                            value
                        }
                    }
                }

                if (recordImpression(now)) {
                    history = MessageDisplayHistory(
                        lastImpression = MessageDisplayHistory.LastImpression(
                            date = now,
                            triggerSessionId = sessionId
                        ),
                        lastDisplay = history.lastDisplay
                    )
                }

                history = MessageDisplayHistory(
                    lastImpression = history.lastImpression,
                    lastDisplay = MessageDisplayHistory.LastDisplay(sessionId)
                )

                historyState.value = history
                displayHistoryStore.set(history, messageId.identifier)
            }

            val data = LayoutEventData(
                event = event,
                context = LayoutEventContext.makeContext(
                    reportingContext = reportingContext,
                    experimentResult = null,
                    layoutContext = layoutContext,
                    displayContext = displayContext.value
                ),
                source = eventSource,
                messageId = messageId,
                renderedLocale = null
            )

            eventRecorder.recordEvent(data)
        }
    }

    override fun recordImpression(date: Long): Boolean {
        if (!shouldRecordImpression()) {
            return false
        }

        val event = MeteredUsageEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityId = messageId.identifier,
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = productId,
            reportingContext = reportingContext,
            timestamp = date,
            contactId = null
        )

        eventRecorder.recordImpressionEvent(event)

        return true
    }

    private fun shouldRecordImpression(): Boolean {
        val lastImpression = historyState.value?.lastImpression ?: return true

        return (clock.currentTimeMillis() - lastImpression.date) >= IMPRESSION_SESSION_LENGTH.inWholeMilliseconds
    }

    private companion object {
        val IMPRESSION_SESSION_LENGTH = 30.minutes
        const val DEFAULT_PRODUCT_ID = "default_native_mc"
    }
}
