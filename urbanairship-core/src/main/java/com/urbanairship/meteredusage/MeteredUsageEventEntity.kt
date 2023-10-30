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

private class UsageTypeConverter {
    @TypeConverter
    fun toUsageType(value: String) = MeteredUsageType.fromString(value)

    @TypeConverter
    fun fromUsageType(type: MeteredUsageType) = type.value
}
