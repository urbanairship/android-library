/* Copyright Airship and Contributors */

package com.urbanairship.automation

import com.urbanairship.automation.deferred.DeferredAutomationData
import com.urbanairship.automation.deferred.isInAppMessage
import com.urbanairship.automation.engine.AutomationScheduleData
import com.urbanairship.automation.engine.AutomationScheduleState
import com.urbanairship.automation.engine.triggerprocessor.TriggerExecutionType
import com.urbanairship.iam.InAppMessage
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import com.urbanairship.json.toJsonList
import com.urbanairship.util.DateUtils
import com.urbanairship.util.VersionUtils
import java.util.Objects
import java.util.UUID
import org.jetbrains.annotations.VisibleForTesting

/**
 * Automation schedule
 */
public class AutomationSchedule @VisibleForTesting internal constructor(
    /**
     * The schedule ID.
     */
    public val identifier: String,
    /**
     * A list of triggers.
     */
    public val triggers: List<AutomationTrigger>,
    /**
     * The schedule group.
     */
    public val group: String? = null,
    /**
     * The priority level.
     */
    public val priority: Int? = null,
    /**
     * The fulfillment limit.
     */
    public val limit: UInt? = null,
    /**
     * The schedule start time in ms.
     */
    public val startDate: ULong? = null,
    /**
     * The schedule end time in ms.
     */
    public val endDate: ULong? = null,
    /**
     * The audience.
     */
    public val audience: AutomationAudience? = null,
    /**
     * An AutomationDelay instance.
     */
    public val delay: AutomationDelay? = null,
    /**
     * The interval in ms.
     */
    public val interval: ULong? = null,
    /**
     * Schedule data
     */
    public val data: ScheduleData,
    /**
     * Whether the schedule could be in a holdout group.
     * @hide
     */
    public val bypassHoldoutGroups: Boolean? = null,
    /**
     * The edit grace period in days.
     */
    public val editGracePeriodDays: ULong? = null,

    internal val metadata: JsonValue? = null,
    internal val frequencyConstraintIds: List<String>? = null,
    internal val messageType: String? = null,
    internal val campaigns: JsonValue? = null,
    internal val reportingContext: JsonValue? = null,
    internal val productId: String? = null,
    internal val minSDKVersion: String? = null,
    internal val created: ULong = System.currentTimeMillis().toULong(),
    internal val queue: String? = null,
    internal val additionalAudienceCheckOverrides: AdditionalAudienceCheckOverrides? = null
) : JsonSerializable {

    internal fun copyWith(
        group: String? = null,
        endDate: ULong? = null,
        metadata: JsonValue? = null): AutomationSchedule {
        return AutomationSchedule(identifier, triggers, group ?: this.group, priority, limit,
            startDate, endDate ?: this.endDate, audience, delay, interval, data,
            bypassHoldoutGroups, editGracePeriodDays, metadata ?: this.metadata,
            frequencyConstraintIds, messageType, campaigns, reportingContext, productId,
            minSDKVersion, created, queue, additionalAudienceCheckOverrides)
    }

    /**
     * Schedule data
     */
    public sealed class ScheduleData: JsonSerializable {
        internal companion object {
            private const val TYPE = "type"
            private const val ACTIONS = "actions"
            private const val MESSAGE = "message"
            private const val DEFERRED = "deferred"

            @Throws(JsonException::class)
            internal fun fromJson(value: JsonValue): ScheduleData {
                val content = value.requireMap()

                return when (ScheduleType.fromJson(content.require(TYPE))) {
                    ScheduleType.ACTIONS -> Actions(content.require(ACTIONS))
                    ScheduleType.IN_APP_MESSAGE -> InAppMessageData(
                        InAppMessage.parseJson(content.require(
                            MESSAGE
                        )))
                    ScheduleType.DEFERRED -> Deferred(
                        DeferredAutomationData.fromJson(content.require(
                            DEFERRED
                        )))
                }
            }
        }

        public data class Actions(public val actions: JsonValue) : ScheduleData() {
            override fun toJsonValue(): JsonValue = jsonMapOf(
                TYPE to ScheduleType.ACTIONS,
                ACTIONS to actions
            ).toJsonValue()
        }

        public data class InAppMessageData(public val message: InAppMessage) : ScheduleData() {
            override fun toJsonValue(): JsonValue = jsonMapOf(
                TYPE to ScheduleType.IN_APP_MESSAGE,
                MESSAGE to message
            ).toJsonValue()
        }

        public data class Deferred internal constructor(internal val deferred: DeferredAutomationData) : ScheduleData() {
            override fun toJsonValue(): JsonValue = jsonMapOf(
                TYPE to ScheduleType.DEFERRED,
                DEFERRED to deferred
            ).toJsonValue()
        }
    }

    public enum class ScheduleType(internal val json: String) : JsonSerializable {
        ACTIONS("actions"),
        IN_APP_MESSAGE("in_app_message"),
        DEFERRED("deferred");

        internal companion object {

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): ScheduleType {
                val content = value.requireString()
                return entries.firstOrNull { it.json == content }
                    ?: throw JsonException("Invalid schedule type $content")
            }
        }

        override fun toJsonValue(): JsonValue = JsonValue.wrap(json)
    }

    internal companion object {
        internal const val DEFAULT_MESSAGE_TYPE = "transactional";
        internal const val TRIGGER_LIMIT = 10

        private const val IDENTIFIER = "id"
        private const val TRIGGERS = "triggers"
        private const val CREATED = "created"
        private const val GROUP = "group"
        private const val METADATA = "metadata"
        private const val PRIORITY = "priority"
        private const val LIMIT = "limit"
        private const val START = "start"
        private const val END = "end"
        private const val AUDIENCE = "audience"
        private const val DELAY = "delay"
        private const val INTERVAL = "interval"
        private const val CAMPAIGNS = "campaigns"
        private const val REPORTING_CONTEXT = "reporting_context"
        private const val PRODUCT_ID = "product_id"
        private const val BYPASS_HOLDOUT_GROUPS = "bypass_holdout_groups"
        private const val EDIT_GRACE_PERIOD_DAYS = "edit_grace_period"
        private const val FREQUENCY_CONSTRAINT_IDS = "frequency_constraint_ids"
        private const val MESSAGE_TYPE = "message_type"
        private const val MIN_SDK_VERSION = "min_sdk_version"
        private const val QUEUE = "queue"
        private const val ADDITIONAL_AUDIENCE_CHECK_OVERRIDES = "additional_audience_check_overrides"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): AutomationSchedule {
            val content = value.requireMap()

            fun parseDate(value: JsonValue?): ULong? {
                val string = value?.optString() ?: return null
                return DateUtils.parseIso8601(string).toULong()
            }

            val created = parseDate(content.get(CREATED))
                ?: throw JsonException("Invalid created date string ${content.get(CREATED)}")

            return AutomationSchedule(
                identifier = content.requireField(IDENTIFIER),
                triggers = content.require(TRIGGERS).requireList().map {
                    AutomationTrigger.fromJson(it, TriggerExecutionType.EXECUTION)
                },
                group = content.optionalField(GROUP),
                metadata = content.get(METADATA),
                priority = content.optionalField(PRIORITY),
                limit = content.get(LIMIT)?.getInt(0)?.toUInt(),
                startDate = parseDate(content.get(START)),
                endDate = parseDate(content.get(END)),
                audience = content.get(AUDIENCE)?.let(AutomationAudience.Companion::fromJson),
                delay = content.get(DELAY)?.let(AutomationDelay.Companion::fromJson),
                interval = content.optionalField(INTERVAL),
                campaigns = content.get(CAMPAIGNS),
                reportingContext = content.get(REPORTING_CONTEXT),
                productId = content.get(PRODUCT_ID)?.requireString(),
                bypassHoldoutGroups = content.optionalField(BYPASS_HOLDOUT_GROUPS),
                editGracePeriodDays = content.optionalField(EDIT_GRACE_PERIOD_DAYS),
                frequencyConstraintIds = content.get(FREQUENCY_CONSTRAINT_IDS)
                    ?.requireList()?.map { it.requireString() },
                messageType = content.optionalField(MESSAGE_TYPE),
                minSDKVersion = content.optionalField(MIN_SDK_VERSION),
                queue = content.optionalField(QUEUE),
                data = ScheduleData.fromJson(value),
                created = created,
                additionalAudienceCheckOverrides = content.get(ADDITIONAL_AUDIENCE_CHECK_OVERRIDES)
                    ?.let(AdditionalAudienceCheckOverrides::fromJson)
            )
        }
    }

    override fun toJsonValue(): JsonValue = JsonMap
        .newBuilder()
        .putAll(data.toJsonValue().optMap())
        .put(IDENTIFIER, identifier)
        .put(TRIGGERS, triggers.map { it.toJsonValue() }.toJsonList())
        .putOpt(GROUP, group)
        .putOpt(METADATA, metadata)
        .putOpt(PRIORITY, priority)
        .putOpt(LIMIT, limit?.toInt())
        .putOpt(START, startDate?.toLong()?.let(DateUtils::createIso8601TimeStamp))
        .putOpt(END, endDate?.toLong()?.let(DateUtils::createIso8601TimeStamp))
        .putOpt(AUDIENCE, audience)
        .putOpt(DELAY, delay)
        .putOpt(INTERVAL, interval?.toLong())
        .putOpt(CAMPAIGNS, campaigns)
        .putOpt(METADATA, metadata)
        .putOpt(PRODUCT_ID, productId)
        .putOpt(BYPASS_HOLDOUT_GROUPS, bypassHoldoutGroups)
        .putOpt(EDIT_GRACE_PERIOD_DAYS, editGracePeriodDays?.toLong())
        .putOpt(FREQUENCY_CONSTRAINT_IDS, frequencyConstraintIds)
        .putOpt(MESSAGE_TYPE, messageType)
        .putOpt(REPORTING_CONTEXT, reportingContext)
        .putOpt(MIN_SDK_VERSION, minSDKVersion)
        .putOpt(QUEUE, queue)
        .put(CREATED, created.toLong().let(DateUtils::createIso8601TimeStamp))
        .putOpt(ADDITIONAL_AUDIENCE_CHECK_OVERRIDES, additionalAudienceCheckOverrides)
        .build()
        .toJsonValue()

    override fun toString(): String = toJsonValue().toString()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AutomationSchedule

        if (identifier != other.identifier) return false
        if (triggers != other.triggers) return false
        if (group != other.group) return false
        if (priority != other.priority) return false
        if (limit != other.limit) return false
        if (startDate != other.startDate) return false
        if (audience != other.audience) return false
        if (delay != other.delay) return false
        if (interval != other.interval) return false
        if (data != other.data) return false
        if (bypassHoldoutGroups != other.bypassHoldoutGroups) return false
        if (editGracePeriodDays != other.editGracePeriodDays) return false
        if (frequencyConstraintIds != other.frequencyConstraintIds) return false
        if (messageType != other.messageType) return false
        if (campaigns != other.campaigns) return false
        if (reportingContext != other.reportingContext) return false
        if (productId != other.productId) return false
        if (minSDKVersion != other.minSDKVersion) return false
        if (created != other.created) return false
        if (queue != other.queue) return false
        if (metadata != other.metadata) return false
        return endDate == other.endDate
    }

    override fun hashCode(): Int {
        return Objects.hash(identifier, triggers, group, priority, limit, startDate, audience, delay,
            interval, data, bypassHoldoutGroups, editGracePeriodDays, frequencyConstraintIds, messageType,
            campaigns, reportingContext, productId, minSDKVersion, created, queue, metadata, endDate)
    }
}

internal fun AutomationSchedule.updateOrCreate(data: AutomationScheduleData?, timestamp: Long): AutomationScheduleData {
    if (data == null) {
        return AutomationScheduleData(
            schedule = this,
            scheduleState = AutomationScheduleState.IDLE,
            scheduleStateChangeDate = timestamp,
            executionCount = 0,
            triggerInfo = null,
            preparedScheduleInfo = null,
            triggerSessionId = UUID.randomUUID().toString()
        )
    }

    data.setSchedule(this)
    return data
}

internal fun AutomationSchedule.isInAppMessageType(): Boolean {
    return when(data) {
        is AutomationSchedule.ScheduleData.Actions -> false
        is AutomationSchedule.ScheduleData.Deferred -> data.deferred.isInAppMessage()
        is AutomationSchedule.ScheduleData.InAppMessageData -> true
    }
}

internal fun AutomationSchedule.isNewSchedule(sinceDate: Long, lastSDKVersion: String?): Boolean {
    if (created.toLong() > sinceDate) {
        return true
    }

    val minSDKVersion = minSDKVersion ?: return false

    // We can skip checking if the min_sdk_version is newer than the current SDK version since
    // remote-data will filter them out. This flag is only a hint to the SDK to treat a schedule with
    // an older created timestamp as a new schedule.

    // If we do not have a last SDK version, then we are coming from an SDK older than
    // 16.2.0. Check for a min SDK version newer or equal to 16.2.0.
    return if (lastSDKVersion == null) {
        VersionUtils.isVersionNewerOrEqualTo("16.2.0", minSDKVersion)
    } else {
        VersionUtils.isVersionNewer(lastSDKVersion, minSDKVersion)
    }
}
