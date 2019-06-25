/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Locale;
import java.util.Set;

/**
 * Job handler for fetching remote data
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataJobHandler {

    /**
     * Action to refresh remote data.
     */
    static final String ACTION_REFRESH = "ACTION_REFRESH";

    private final RemoteDataApiClient apiClient;
    private final RemoteData remoteData;
    private final LocaleManager localeManager;

    /**
     * RemoteDataJobHandler constructor.
     *
     * @param context The application context.
     * @param airship A UAirship instance.
     */
    RemoteDataJobHandler(@NonNull Context context, @NonNull UAirship airship) {
        this(airship.getRemoteData(), new RemoteDataApiClient(airship.getAirshipConfigOptions()), LocaleManager.shared(context));
    }

    /**
     * RemoteDataJobHandler constructor.
     *
     * @param apiClient The RemoteDataApiClient.
     * @param remoteData The remote data instance.
     * @param localeManager The locale manager.
     */
    @VisibleForTesting
    RemoteDataJobHandler(@NonNull RemoteData remoteData, @NonNull RemoteDataApiClient apiClient, @NonNull LocaleManager localeManager) {
        this.apiClient = apiClient;
        this.remoteData = remoteData;
        this.localeManager = localeManager;
    }

    /**
     * Called to handle jobs from {@link RemoteData#onPerformJob(UAirship, JobInfo)}.
     *
     * @param jobInfo The jobInfo.
     * @return The job result.
     */
    @JobInfo.JobResult
    protected int performJob(@NonNull JobInfo jobInfo) {
        switch (jobInfo.getAction()) {
            case ACTION_REFRESH:
                return onRefresh();
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Refreshes the remote data, performing a callback into RemoteData if there
     * is anything new to process.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onRefresh() {
        String lastModified = remoteData.getLastModified();
        Locale locale = localeManager.getDefaultLocale();
        Response response = apiClient.fetchRemoteData(lastModified, locale);

        if (response == null) {
            Logger.debug("Unable to connect to remote data server, retrying later");
            return JobInfo.JOB_RETRY;
        }

        int status = response.getStatus();

        // Success
        if (status == 200) {
            String body = response.getResponseBody();
            if (UAStringUtil.isEmpty(body)) {
                Logger.error("Remote data missing response body");
                return JobInfo.JOB_FINISHED;
            }

            Logger.debug("Received remote data response: %s", body);

            lastModified = response.getResponseHeader("Last-Modified");
            JsonMap metadata = RemoteData.createMetadata(locale);

            try {
                JsonValue json = JsonValue.parseString(body);
                JsonMap map = json.optMap();
                if (map.containsKey("payloads")) {
                    Set<RemoteDataPayload> payloads = RemoteDataPayload.parsePayloads(map.opt("payloads"), metadata);
                    remoteData.onNewData(payloads, lastModified, metadata);
                    remoteData.onRefreshFinished();
                    return JobInfo.JOB_FINISHED;
                }
            } catch (JsonException e) {
                Logger.error("Unable to parse body: %s", body);
                return JobInfo.JOB_FINISHED;
            }

        } else if (status == 304) {
            // Not modified
            Logger.debug("Remote data not modified since last refresh");
            remoteData.onRefreshFinished();
            return JobInfo.JOB_FINISHED;
        } else {
            // Error
            Logger.debug("Error fetching remote data: %s", String.valueOf(status));
            return JobInfo.JOB_RETRY;
        }

        return JobInfo.JOB_FINISHED;
    }

}
