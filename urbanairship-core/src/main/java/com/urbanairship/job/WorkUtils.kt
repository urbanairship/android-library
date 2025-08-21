package com.urbanairship.job

import androidx.work.Data
import com.urbanairship.job.JobInfo.Companion.newBuilder
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import kotlin.time.Duration.Companion.milliseconds

internal object WorkUtils {

    private const val ACTION = "action"
    private const val EXTRAS = "extras"
    private const val COMPONENT = "component"
    private const val NETWORK_REQUIRED = "network_required"
    private const val MIN_DELAY = "min_delay"
    private const val CONFLICT_STRATEGY = "conflict_strategy"
    private const val INITIAL_BACKOFF = "initial_backoff"
    private const val RATE_LIMIT_IDS = "rate_limit_ids"

    fun convertToData(jobInfo: JobInfo): Data {
        return Data.Builder().putString(ACTION, jobInfo.action)
            .putString(EXTRAS, jobInfo.extras.toString())
            .putString(COMPONENT, jobInfo.airshipComponentName)
            .putBoolean(NETWORK_REQUIRED, jobInfo.isNetworkAccessRequired)
            .putLong(MIN_DELAY, jobInfo.minDelay.inWholeMilliseconds)
            .putLong(INITIAL_BACKOFF, jobInfo.initialBackOff.inWholeMilliseconds)
            .putInt(CONFLICT_STRATEGY, jobInfo.conflictStrategy.rawValue)
            .putString(RATE_LIMIT_IDS, JsonValue.wrapOpt(jobInfo.rateLimitIds).toString())
            .build()
    }

    @Throws(JsonException::class)
    fun convertToJobInfo(data: Data): JobInfo {
        val builder = newBuilder()
            .setAction(data.getString(ACTION) ?: "")
            .setExtras(JsonValue.parseString(data.getString(EXTRAS)).optMap())
            .setMinDelay(data.getLong(MIN_DELAY, 0).milliseconds)
            .setInitialBackOff(data.getLong(INITIAL_BACKOFF, 0).milliseconds)
            .setNetworkAccessRequired(data.getBoolean(NETWORK_REQUIRED, false))

        data.getInt(CONFLICT_STRATEGY, JobInfo.ConflictStrategy.REPLACE.rawValue)
            .let {
                val strategy = JobInfo.ConflictStrategy.fromRawValue(it) ?: JobInfo.ConflictStrategy.REPLACE
                builder.setConflictStrategy(strategy)
            }

        data.getString(COMPONENT)?.let(builder::setAirshipComponent)

        for (value in JsonValue.parseString(data.getString(RATE_LIMIT_IDS)).optList()) {
            builder.addRateLimit(value.requireString())
        }

        return builder.build()
    }
}
