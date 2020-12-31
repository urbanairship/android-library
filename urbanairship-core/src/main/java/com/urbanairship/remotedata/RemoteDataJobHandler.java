/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import android.content.Context;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Locale;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

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
        this(airship.getRemoteData(),
                new RemoteDataApiClient(airship.getRuntimeConfig(), airship.getPushProviders()),
                UAirship.shared().getLocaleManager());
    }

    /**
     * RemoteDataJobHandler constructor.
     *
     * @param apiClient The RemoteDataApiClient.
     * @param remoteData The remote data instance.
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
        if (ACTION_REFRESH.equals(jobInfo.getAction())) {
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
        Locale locale = localeManager.getLocale();
        Response<Set<RemoteDataPayload>> response;
        try {
            response = apiClient.fetchRemoteData(lastModified, locale);
        } catch (RequestException e) {
            Logger.error(e, "RemoteDataJobHandler - Failed to refresh data");
            return JobInfo.JOB_FINISHED;
        }

        int status = response.getStatus();

        // Success
        if (status == 200) {
            String body = response.getResponseBody();
            if (UAStringUtil.isEmpty(body)) {
                Logger.error("Remote data missing response body");
                return JobInfo.JOB_FINISHED;
            }

            lastModified = response.getResponseHeader("Last-Modified");
            JsonMap metadata = RemoteData.createMetadata(locale);
            remoteData.onNewData((Set<RemoteDataPayload>) response.getResult(), lastModified, metadata);
            remoteData.onRefreshFinished();
            Logger.debug("Received remote data response: %s", body);
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
