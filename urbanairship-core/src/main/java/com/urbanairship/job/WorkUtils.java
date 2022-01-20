package com.urbanairship.job;

import android.telecom.Call;

import com.urbanairship.base.Extender;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;

abstract class WorkUtils {

    private static final String ACTION_KEY = "action";
    private static final String EXTRAS = "extras";
    private static final String COMPONENT = "component";
    private static final String NETWORK_REQUIRED = "network_required";
    private static final String INITIAL_DELAY = "initial_delay";
    private static final String CONFLICT_STRATEGY = "conflict_strategy";

    @NonNull
    static Data convertToData(@NonNull JobInfo jobInfo) {
        return new Data.Builder()
                .putString(ACTION_KEY, jobInfo.getAction())
                .putString(EXTRAS, jobInfo.getExtras().toString())
                .putString(COMPONENT, jobInfo.getAirshipComponentName())
                .putBoolean(NETWORK_REQUIRED, jobInfo.isNetworkAccessRequired())
                .putLong(INITIAL_DELAY, jobInfo.getInitialDelay())
                .putInt(CONFLICT_STRATEGY, jobInfo.getConflictStrategy())
                .build();
    }

    @NonNull
    static JobInfo convertToJobInfo(@NonNull Data data) throws JsonException {
        return convertToJobInfo(data, null);
    }

    @NonNull
    static JobInfo convertToJobInfo(@NonNull Data data, @Nullable Extender<JobInfo.Builder> extender) throws JsonException {
        JobInfo.Builder builder = JobInfo.newBuilder()
                      .setAction(data.getString(ACTION_KEY))
                      .setExtras(JsonValue.parseString(data.getString(EXTRAS)).optMap())
                      .setInitialDelay(data.getLong(INITIAL_DELAY, 0), TimeUnit.MILLISECONDS)
                      .setNetworkAccessRequired(data.getBoolean(NETWORK_REQUIRED, false))
                      .setAirshipComponent(data.getString(COMPONENT))
                      .setConflictStrategy(data.getInt(CONFLICT_STRATEGY, JobInfo.REPLACE));

        if (extender != null) {
            builder = extender.extend(builder);
        }

        return builder.build();
    }
}
