/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import com.urbanairship.AirshipComponent
import com.urbanairship.json.JsonMap
import com.urbanairship.json.jsonMapOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Airship job for defining a unit of work to be performed in an [AirshipComponent].
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobInfo private constructor(builder: Builder) {

    public enum class ConflictStrategy(internal val rawValue: Int) {
        REPLACE(0), APPEND(1), KEEP(2);

        internal companion object {
            fun fromRawValue(value: Int): ConflictStrategy? {
                return entries.firstOrNull { it.rawValue == value }
            }
        }
    }

    /**
     * The job's action.
     *
     * @return The job's action.
     */
    public val action: String

    /**
     * The [AirshipComponent] name that will receive the job.
     *
     * @return The [AirshipComponent] class name.
     */
    public val airshipComponentName: String

    /**
     * If network access is required for the job.
     *
     * @return `true` if network access is required, otherwise `false`.
     */
    public val isNetworkAccessRequired: Boolean

    /**
     * Gets the initial delay in milliseconds.
     *
     * @return The initial delay in milliseconds.
     */
    public val minDelay: Duration

    public val conflictStrategy: ConflictStrategy

    public val initialBackOff: Duration

    /**
     * The job's extras.
     *
     * @return The job's extras.
     */
    public val extras: JsonMap
    public val rateLimitIds: Set<String>

    /**
     * Default constructor.
     *
     * @param builder A builder instance.
     */
    init {
        this.action = builder.action
        this.airshipComponentName = builder.airshipComponentName ?: ""
        this.extras = builder.extras ?: jsonMapOf()
        this.isNetworkAccessRequired = builder.isNetworkAccessRequired
        this.minDelay = builder.minDelay
        this.conflictStrategy = builder.conflictStrategy
        this.initialBackOff = builder.initialBackOff
        this.rateLimitIds = HashSet(builder.rateLimitIds)
    }

    override fun toString(): String {
        return "JobInfo{action='$action', airshipComponentName='$airshipComponentName', " +
                "isNetworkAccessRequired=$isNetworkAccessRequired, minDelayMs=$minDelay, " +
                "conflictStrategy=$conflictStrategy, initialBackOffMs=$initialBackOff, " +
                "extras=$extras, rateLimitIds=$rateLimitIds}"
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val jobInfo = o as JobInfo
        return isNetworkAccessRequired == jobInfo.isNetworkAccessRequired
                && minDelay == jobInfo.minDelay
                && conflictStrategy == jobInfo.conflictStrategy
                && initialBackOff == jobInfo.initialBackOff
                && ObjectsCompat.equals(extras, jobInfo.extras)
                && ObjectsCompat.equals(action, jobInfo.action)
                && ObjectsCompat.equals(airshipComponentName, jobInfo.airshipComponentName)
                && ObjectsCompat.equals(rateLimitIds, jobInfo.rateLimitIds)
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(
            extras,
            action,
            airshipComponentName,
            isNetworkAccessRequired,
            minDelay,
            conflictStrategy,
            initialBackOff,
            rateLimitIds
        )
    }

    /**
     * JobInfo builder.
     */
    public class Builder {
        internal var action: String = ""
        public var airshipComponentName: String? = null
            private set
        public var isNetworkAccessRequired: Boolean = false
            private set
        public var extras: JsonMap? = null
            private set
        public var conflictStrategy: ConflictStrategy = ConflictStrategy.REPLACE
            private set
        public var initialBackOff: Duration = MIN_INITIAL_BACKOFF
            private set
        public var minDelay: Duration = 0.seconds
            private set
        public val rateLimitIds: MutableSet<String> = mutableSetOf()

        /**
         * The job's action.
         *
         * @param action The job's action.
         * @return The job builder.
         */
        public fun setAction(action: String): Builder {
            return this.also { it.action = action }
        }

        public fun setInitialBackOff(duration: Duration): Builder {
            return this.also { it.initialBackOff = maxOf(MIN_INITIAL_BACKOFF, duration) }
        }

        /**
         * Sets if network access is required for the job.
         *
         * @param isNetworkAccessRequired Flag if network access is required.
         * @return The job builder.
         */
        public fun setNetworkAccessRequired(isNetworkAccessRequired: Boolean): Builder {
            return this.also { it.isNetworkAccessRequired = isNetworkAccessRequired }
        }

        /**
         * Sets the [AirshipComponent] that will receive the job.
         *
         * @param component The airship component.
         * @return The job builder.
         */
        public fun setAirshipComponent(component: Class<out AirshipComponent>): Builder {
            return this.also { it.airshipComponentName = component.name }
        }

        /**
         * Sets the min delay.
         *
         * @param delay The initial delay.
         * @return The job builder.
         */
        public fun setMinDelay(delay: Duration): Builder {
            return this.also { it.minDelay = delay }
        }

        /**
         * Sets the [AirshipComponent] that will receive the job.
         *
         * @param componentName The airship component name.
         * @return The job builder.
         */
        public fun setAirshipComponent(componentName: String): Builder {
            return this.also { it.airshipComponentName = componentName }
        }

        /**
         * Sets the extras for the job.
         *
         * @param extras Bundle of extras.
         * @return The job builder.
         */
        public fun setExtras(extras: JsonMap): Builder {
            return this.also { it.extras = extras }
        }

        public fun setConflictStrategy(conflictStrategy: ConflictStrategy): Builder {
            return this.also { it.conflictStrategy = conflictStrategy }
        }

        public fun addRateLimit(rateLimitId: String): Builder {
            return this.also { it.rateLimitIds.add(rateLimitId) }
        }

        /**
         * Builds the job.
         *
         * @return The job.
         */
        public fun build(): JobInfo {
            if (action.isEmpty()) {
                throw IllegalArgumentException("Missing action.")
            }
            return JobInfo(this)
        }

        private companion object {
            private val MIN_INITIAL_BACKOFF = 30.seconds
        }
    }

    public companion object {
        /**
         * Creates a new job builder.
         *
         * @return A job builder.
         */
        public fun newBuilder(): Builder {
            return Builder()
        }
    }
}
