package com.urbanairship.job;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.work.Data;

abstract class WorkUtils {

    private static final String ACTION = "action";
    private static final String EXTRAS = "extras";
    private static final String COMPONENT = "component";
    private static final String NETWORK_REQUIRED = "network_required";
    private static final String MIN_DELAY = "min_delay";
    private static final String CONFLICT_STRATEGY = "conflict_strategy";
    private static final String INITIAL_BACKOFF = "initial_backoff";
    private static final String RATE_LIMIT_IDS = "rate_limit_ids";

    @NonNull
    static Data convertToData(@NonNull JobInfo jobInfo) {
        return new Data.Builder()
                .putString(ACTION, jobInfo.getAction())
                .putString(EXTRAS, jobInfo.getExtras().toString())
                .putString(COMPONENT, jobInfo.getAirshipComponentName())
                .putBoolean(NETWORK_REQUIRED, jobInfo.isNetworkAccessRequired())
                .putLong(MIN_DELAY, jobInfo.getMinDelayMs())
                .putLong(INITIAL_BACKOFF, jobInfo.getInitialBackOffMs())
                .putInt(CONFLICT_STRATEGY, jobInfo.getConflictStrategy())
                .putString(RATE_LIMIT_IDS, JsonValue.wrapOpt(jobInfo.getRateLimitIds()).toString())
                .build();
    }

    @NonNull
    static JobInfo convertToJobInfo(@NonNull Data data) throws JsonException {
        JobInfo.Builder builder = JobInfo.newBuilder()
                                         .setAction(data.getString(ACTION))
                                         .setExtras(JsonValue.parseString(data.getString(EXTRAS)).optMap())
                                         .setMinDelay(data.getLong(MIN_DELAY, 0), TimeUnit.MILLISECONDS)
                                         .setInitialBackOff(data.getLong(INITIAL_BACKOFF, 0), TimeUnit.MILLISECONDS)
                                         .setNetworkAccessRequired(data.getBoolean(NETWORK_REQUIRED, false))
                                         .setAirshipComponent(data.getString(COMPONENT))
                                         .setConflictStrategy(data.getInt(CONFLICT_STRATEGY, JobInfo.REPLACE));

        for (JsonValue value : JsonValue.parseString(data.getString(RATE_LIMIT_IDS)).optList()) {
            builder.addRateLimit(value.requireString());
        }

        return builder.build();
    }

}
