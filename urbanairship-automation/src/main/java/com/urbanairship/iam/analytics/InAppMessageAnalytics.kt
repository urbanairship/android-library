package com.urbanairship.iam.analytics

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.iam.InAppMessage
import com.urbanairship.iam.analytics.events.InAppEvent
import com.urbanairship.experiment.ExperimentResult
import com.urbanairship.json.JsonValue
import com.urbanairship.meteredusage.AirshipMeteredUsage
import com.urbanairship.meteredusage.MeteredUsageEventEntity
import com.urbanairship.meteredusage.MeteredUsageType
import com.urbanairship.util.Clock
import java.util.UUID

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface InAppMessageAnalyticsInterface {
    public fun recordEvent(event: InAppEvent, layoutContext: LayoutData?)
    public suspend fun recordImpression()
}

internal sealed class LoggingInAppMessageAnalytics: InAppMessageAnalyticsInterface {
    override fun recordEvent(event: InAppEvent, layoutContext: LayoutData?) {
        UALog.d { "recording event $event with context: $layoutContext" }
    }

    override suspend fun recordImpression() {
        UALog.d { "Logging impression" }
    }
}

internal class InAppMessageAnalytics private constructor(
    private val messageId: InAppEventMessageId,
    private val source: InAppEventSource,
    private val renderedLocale: JsonValue?,
    private val reportingMetadata: JsonValue?,
    private val experimentResult: ExperimentResult?,
    private val eventRecorder: InAppEventRecorderInterface,
    private val impressionRecorder: AirshipMeteredUsage,
    private val isReportingEnabled: Boolean,
    private val productId: String?,
    private val contactId: String?,
    private val clock: Clock
): InAppMessageAnalyticsInterface {

    constructor(scheduleId: String,
                productId: String?,
                contactId: String?,
                message: InAppMessage,
                campaigns: JsonValue?,
                reportingMetadata: JsonValue?,
                experimentResult: ExperimentResult?,
                eventRecorder: InAppEventRecorderInterface,
                impressionRecorder: AirshipMeteredUsage,
                clock: Clock = Clock.DEFAULT_CLOCK) :
            this(
                messageId = makeMessageID(message, scheduleId, campaigns),
                source = makeEventSource(message),
                productId = productId,
                contactId = contactId,
                renderedLocale = message.renderedLocale,
                reportingMetadata = reportingMetadata,
                experimentResult = experimentResult,
                eventRecorder = eventRecorder,
                impressionRecorder = impressionRecorder,
                isReportingEnabled = message.isReportingEnabled ?: true,
                clock = clock
            )

    private companion object {
        fun makeMessageID(message: InAppMessage, scheduleID: String, campaigns: JsonValue?): InAppEventMessageId {
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
        if (!isReportingEnabled) {
            return
        }

        val data = InAppEventData(
            event = event,
            context = InAppEventContext.makeContext(
                reportingContext = reportingMetadata,
                experimentResult = experimentResult,
                layoutContext = layoutContext
            ),
            source = source,
            messageId = messageId,
            renderedLocale = renderedLocale
        )

        eventRecorder.recordEvent(data)
    }

    override suspend fun recordImpression() {
        val productId = productId ?: return

        val impression = MeteredUsageEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityId = messageId.identifier,
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = productId,
            reportingContext = reportingMetadata,
            timestamp = clock.currentTimeMillis(),
            contactId = contactId
        )

        impressionRecorder.addEvent(impression)
    }
}
