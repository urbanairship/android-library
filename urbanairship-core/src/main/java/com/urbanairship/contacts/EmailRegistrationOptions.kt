/* Copyright Airship and Contributors */
package com.urbanairship.contacts

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.time.Instant

/**
 * Email channel registration options.
 */
public class EmailRegistrationOptions private constructor(
    public val transactionalOptedIn: Instant?,
    public val commercialOptedIn: Instant?,
    public val properties: JsonMap?,
    public val isDoubleOptIn: Boolean,
    public val openTracking: TrackingConsent?,
    public val clickTracking: TrackingConsent?
) : JsonSerializable {

    /**
     * An email tracking choice. `null` in its place sends neither date, leaving the project
     * default or the channel's earlier choice.
     */
    public sealed class TrackingConsent {
        /** The date the choice was made. */
        public abstract val date: Instant

        /** Opted in to tracking as of [date]. */
        public data class OptedIn(override val date: Instant) : TrackingConsent()

        /** Opted out of tracking as of [date]. */
        public data class OptedOut(override val date: Instant) : TrackingConsent()
    }

    @Throws(JsonException::class)
    override fun toJsonValue(): JsonValue = jsonMapOf(
        TRANSACTIONAL_OPTED_IN_KEY to transactionalOptedIn?.toEpochMilli(),
        COMMERCIAL_OPTED_IN_KEY to commercialOptedIn?.toEpochMilli(),
        PROPERTIES_KEY to properties,
        DOUBLE_OPT_IN_KEY to isDoubleOptIn,
        OPEN_TRACKING_OPTED_IN_KEY to openTracking.optedInDate()?.toEpochMilli(),
        OPEN_TRACKING_OPTED_OUT_KEY to openTracking.optedOutDate()?.toEpochMilli(),
        CLICK_TRACKING_OPTED_IN_KEY to clickTracking.optedInDate()?.toEpochMilli(),
        CLICK_TRACKING_OPTED_OUT_KEY to clickTracking.optedOutDate()?.toEpochMilli(),
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EmailRegistrationOptions

        if (transactionalOptedIn != other.transactionalOptedIn) return false
        if (commercialOptedIn != other.commercialOptedIn) return false
        if (properties != other.properties) return false
        if (isDoubleOptIn != other.isDoubleOptIn) return false
        if (openTracking != other.openTracking) return false
        if (clickTracking != other.clickTracking) return false

        return true
    }

    override fun hashCode(): Int = ObjectsCompat.hash(
        transactionalOptedIn, commercialOptedIn, properties, isDoubleOptIn, openTracking, clickTracking
    )

    override fun toString(): String {
        return "EmailRegistrationOptions(transactionalOptedIn=$transactionalOptedIn, commercialOptedIn=$commercialOptedIn, properties=$properties, isDoubleOptIn=$isDoubleOptIn, openTracking=$openTracking, clickTracking=$clickTracking)"
    }

    public companion object {

        private const val TRANSACTIONAL_OPTED_IN_KEY = "transactional_opted_in"
        private const val COMMERCIAL_OPTED_IN_KEY = "commercial_opted_in"
        private const val PROPERTIES_KEY = "properties"
        private const val DOUBLE_OPT_IN_KEY = "double_opt_in"
        private const val OPEN_TRACKING_OPTED_IN_KEY = "open_tracking_opted_in"
        private const val OPEN_TRACKING_OPTED_OUT_KEY = "open_tracking_opted_out"
        private const val CLICK_TRACKING_OPTED_IN_KEY = "click_tracking_opted_in"
        private const val CLICK_TRACKING_OPTED_OUT_KEY = "click_tracking_opted_out"

        /**
         * Commercial registration options.
         *
         * @param commercialOptedIn The commercial opted in date. Callers holding a
         * `java.util.Date` should pass `date.toInstant()`.
         * @param transactionalOptedIn The transactional opted in date.
         * @param properties The optional properties.
         * @param openTracking The open tracking choice, or `null` to send none.
         * @param clickTracking The click tracking choice, or `null` to send none.
         * @return The registration options.
         */
        @JvmStatic
        @JvmOverloads
        public fun commercialOptions(
            commercialOptedIn: Instant? = null,
            transactionalOptedIn: Instant? = null,
            properties: JsonMap? = null,
            openTracking: TrackingConsent? = null,
            clickTracking: TrackingConsent? = null
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn = transactionalOptedIn,
                commercialOptedIn = commercialOptedIn,
                properties = properties,
                isDoubleOptIn = false,
                openTracking = openTracking,
                clickTracking = clickTracking
            )
        }

        /**
         * Email registration options.
         *
         * @param transactionalOptedIn The transactional opted in date.
         * @param properties The optional properties.
         * @param doubleOptIn `true` to enable double opt-in, otherwise `false`.
         * @param openTracking The open tracking choice, or `null` to send none.
         * @param clickTracking The click tracking choice, or `null` to send none.
         * @return The registration options.
         */
        @JvmStatic
        @JvmOverloads
        public fun options(
            transactionalOptedIn: Instant? = null,
            properties: JsonMap? = null,
            doubleOptIn: Boolean,
            openTracking: TrackingConsent? = null,
            clickTracking: TrackingConsent? = null
        ): EmailRegistrationOptions {
            return EmailRegistrationOptions(
                transactionalOptedIn = transactionalOptedIn,
                commercialOptedIn = null,
                properties = properties,
                isDoubleOptIn = doubleOptIn,
                openTracking = openTracking,
                clickTracking = clickTracking
            )
        }

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public fun fromJson(value: JsonValue): EmailRegistrationOptions {
            val map = value.optMap()
            return EmailRegistrationOptions(
                transactionalOptedIn = map.optDate(TRANSACTIONAL_OPTED_IN_KEY),
                commercialOptedIn = map.optDate(COMMERCIAL_OPTED_IN_KEY),
                properties = map.opt(PROPERTIES_KEY).map,
                isDoubleOptIn = map.opt(DOUBLE_OPT_IN_KEY).getBoolean(false),
                openTracking = map.optTracking(OPEN_TRACKING_OPTED_IN_KEY, OPEN_TRACKING_OPTED_OUT_KEY),
                clickTracking = map.optTracking(CLICK_TRACKING_OPTED_IN_KEY, CLICK_TRACKING_OPTED_OUT_KEY)
            )
        }
    }
}

/** The date if this is an opt-in, otherwise `null`. */
internal fun EmailRegistrationOptions.TrackingConsent?.optedInDate(): Instant? =
    (this as? EmailRegistrationOptions.TrackingConsent.OptedIn)?.date

/** The date if this is an opt-out, otherwise `null`. */
internal fun EmailRegistrationOptions.TrackingConsent?.optedOutDate(): Instant? =
    (this as? EmailRegistrationOptions.TrackingConsent.OptedOut)?.date

/**
 * Reads a persisted epoch-millis date. Options persisted by older SDK versions encoded "unset" as
 * -1 rather than omitting the field, so any non-positive value reads as unset.
 */
private fun JsonMap.optDate(key: String): Instant? =
    opt(key).takeIf { it.isNumber }?.getLong(0)?.takeIf { it > 0 }?.let(Instant::ofEpochMilli)

private fun JsonMap.optTracking(
    optedInKey: String,
    optedOutKey: String
): EmailRegistrationOptions.TrackingConsent? =
    optDate(optedInKey)?.let { EmailRegistrationOptions.TrackingConsent.OptedIn(it) }
        ?: optDate(optedOutKey)?.let { EmailRegistrationOptions.TrackingConsent.OptedOut(it) }
