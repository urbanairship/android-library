/* Copyright Airship and Contributors */
package com.urbanairship.job

import androidx.work.Data
import com.urbanairship.job.JobInfo.Companion.newBuilder
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.milliseconds

internal object WorkUtils {

    private object Keys {
        const val ACTION = "action"
        const val EXTRAS = "extras"
        const val COMPONENT = "component"
        const val NETWORK_REQUIRED = "network_required"
        const val MIN_DELAY = "min_delay"
        const val CONFLICT_STRATEGY = "conflict_strategy"
        const val INITIAL_BACKOFF = "initial_backoff"
        const val RATE_LIMIT_IDS = "rate_limit_ids"
    }

    fun convertToData(jobInfo: JobInfo): Data {
        return Data.Builder().putString(Keys.ACTION, jobInfo.action)
            .putString(Keys.EXTRAS, jobInfo.extras.toString())
            .putString(Keys.COMPONENT, jobInfo.scope)
            .putBoolean(Keys.NETWORK_REQUIRED, jobInfo.isNetworkAccessRequired)
            .putLong(Keys.MIN_DELAY, jobInfo.minDelay.inWholeMilliseconds)
            .putLong(Keys.INITIAL_BACKOFF, jobInfo.initialBackOff.inWholeMilliseconds)
            .putInt(Keys.CONFLICT_STRATEGY, jobInfo.conflictStrategy.rawValue)
            .putString(Keys.RATE_LIMIT_IDS, JsonValue.wrapOpt(jobInfo.rateLimitIds).toString())
            .build()
    }

    @Throws(JsonException::class)
    fun convertToJobInfo(data: Data): JobInfo {
        val builder = newBuilder()
            .setAction(data.getString(Keys.ACTION) ?: "")
            .setExtras(JsonValue.parseString(data.getString(Keys.EXTRAS)).optMap())
            .setMinDelay(data.getLong(Keys.MIN_DELAY, 0).milliseconds)
            .setInitialBackOff(data.getLong(Keys.INITIAL_BACKOFF, 0).milliseconds)
            .setNetworkAccessRequired(data.getBoolean(Keys.NETWORK_REQUIRED, false))

        data.getInt(Keys.CONFLICT_STRATEGY, JobInfo.ConflictStrategy.REPLACE.rawValue)
            .let {
                val strategy = JobInfo.ConflictStrategy.fromRawValue(it) ?: JobInfo.ConflictStrategy.REPLACE
                builder.setConflictStrategy(strategy)
            }

        data.getString(Keys.COMPONENT)?.let(builder::setScope)

        for (value in JsonValue.parseString(data.getString(Keys.RATE_LIMIT_IDS)).optList()) {
            builder.addRateLimit(value.requireString())
        }

        return builder.build()
    }
}
