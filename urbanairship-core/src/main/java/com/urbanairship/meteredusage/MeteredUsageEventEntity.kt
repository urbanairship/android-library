/* Copyright Airship and Contributors */

package com.urbanairship.meteredusage

import androidx.annotation.OpenForTesting
import androidx.annotation.RestrictTo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.urbanairship.json.JsonTypeConverters
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.DateUtils

/**
 * Entities stored in the metered usage database.\
 * @hide
 */
@OpenForTesting
@Entity(tableName = "events")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@TypeConverters(UsageTypeConverter::class, JsonTypeConverters::class)
public data class MeteredUsageEventEntity(
    @PrimaryKey val eventId: String,
    val entityId: String?,
    val type: MeteredUsageType,
    val product: String,
    val reportingContext: JsonValue?,
    val timestamp: Long?,
    val contactId: String?
) {
    internal fun withAnalyticsDisabled(): MeteredUsageEventEntity {
        return MeteredUsageEventEntity(
            eventId = this.eventId,
            entityId = null,
            type = this.type,
            product = this.product,
            reportingContext = null,
            timestamp = null,
            contactId = null
        )
    }

    internal fun copyWithContactId(contactId: String): MeteredUsageEventEntity {
        return MeteredUsageEventEntity(
            eventId = this.eventId,
            entityId = this.entityId,
            type = this.type,
            product = this.product,
            reportingContext = this.reportingContext,
            timestamp = this.timestamp,
            contactId = contactId
        )
    }

    internal fun toJson(): JsonValue {
        return jsonMapOf(
            "event_id" to eventId,
            "usage_type" to type.value,
            "product" to product,
            "reporting_context" to reportingContext,
            "occurred" to timestamp?.let { DateUtils.createIso8601TimeStamp(it) },
            "entity_id" to entityId,
            "contact_id" to contactId
        ).toJsonValue()
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class MeteredUsageType(public val value: String) {
    IN_APP_EXPERIENCE_IMPRESSION("iax_impression");

    internal companion object {

        @Throws(IllegalStateException::class)
        fun fromString(string: String): MeteredUsageType {
            return when (string) {
                IN_APP_EXPERIENCE_IMPRESSION.value -> IN_APP_EXPERIENCE_IMPRESSION
                else -> throw IllegalStateException("Invalid metered usage type")
            }
        }
    }
}

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class UsageTypeConverter {
    @TypeConverter
    public fun toUsageType(value: String): MeteredUsageType = MeteredUsageType.fromString(value)

    @TypeConverter
    public fun fromUsageType(type: MeteredUsageType): String = type.value
}
