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
    private val messageID: InAppEventMessageID,
    private val source: InAppEventSource,
    private val renderedLocale: Map<String, JsonValue>?,
    private val reportingMetadata: JsonValue?,
    private val experimentResult: ExperimentResult?,
    private val eventRecorder: InAppEventRecorderInterface,
    private val impressionRecorder: AirshipMeteredUsage,
    private val isReportingEnabled: Boolean,
    private val productID: String?,
    private val contactID: String?,
    private val clock: Clock
): InAppMessageAnalyticsInterface {

    constructor(scheduleID: String,
                productID: String?,
                contactID: String?,
                message: InAppMessage,
                campaigns: JsonValue?,
                reportingMetadata: JsonValue?,
                experimentResult: ExperimentResult?,
                eventRecorder: InAppEventRecorderInterface,
                impressionRecorder: AirshipMeteredUsage,
                clock: Clock = Clock.DEFAULT_CLOCK) :
            this(
                messageID = makeMessageID(message, scheduleID, campaigns),
                source = makeEventSource(message),
                productID = productID,
                contactID = contactID,
                renderedLocale = message.renderedLocale,
                reportingMetadata = reportingMetadata,
                experimentResult = experimentResult,
                eventRecorder = eventRecorder,
                impressionRecorder = impressionRecorder,
                isReportingEnabled = message.isReportingEnabled ?: true,
                clock = clock
            )

    private companion object {
        fun makeMessageID(message: InAppMessage, scheduleID: String, campaigns: JsonValue?): InAppEventMessageID {
            return when(message.source ?: InAppMessage.InAppMessageSource.REMOTE_DATA) {
                InAppMessage.InAppMessageSource.REMOTE_DATA -> InAppEventMessageID.AirshipID(
                    scheduleID,
                    campaigns
                )
                InAppMessage.InAppMessageSource.APP_DEFINED -> InAppEventMessageID.AppDefined(
                    scheduleID
                )
                InAppMessage.InAppMessageSource.LEGACY_PUSH -> InAppEventMessageID.Legacy(scheduleID)
            }
        }

        fun makeEventSource(message: InAppMessage): InAppEventSource {
             return when(message.source ?: InAppMessage.InAppMessageSource.REMOTE_DATA) {
                InAppMessage.InAppMessageSource.APP_DEFINED -> InAppEventSource.APP_DEFINED
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
            messageID = messageID,
            renderedLocale = renderedLocale ?: mapOf()
        )

        eventRecorder.recordEvent(data)
    }

    override suspend fun recordImpression() {
        val productID = productID ?: return

        val impression = MeteredUsageEventEntity(
            eventId = UUID.randomUUID().toString(),
            entityId = messageID.identifier,
            type = MeteredUsageType.IN_APP_EXPERIENCE_IMPRESSION,
            product = productID,
            reportingContext = reportingMetadata,
            timestamp = clock.currentTimeMillis(),
            contactId = contactID
        )

        impressionRecorder.addEvent(impression)
    }
}
