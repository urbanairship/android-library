/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import androidx.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.job.JobInfo;

/**
 * Handles intents for {@link Analytics#onPerformJob(UAirship, JobInfo)}.
 */
class AnalyticsJobHandler {

    private final UAirship airship;
    private final EventManager eventManager;

    AnalyticsJobHandler(@NonNull UAirship airship, @NonNull EventManager eventManager) {
        this.airship = airship;
        this.eventManager = eventManager;
    }

    @JobInfo.JobResult
    public int performJob(@NonNull JobInfo jobInfo) {
        Logger.verbose("AnalyticsJobHandler - Received jobInfo with action: %s", jobInfo.getAction());

        switch (jobInfo.getAction()) {
            case Analytics.ACTION_SEND:
                return onUploadEvents();

            default:
                Logger.warn("AnalyticsJobHandler - Unrecognized jobInfo with action: %s", jobInfo.getAction());
                return JobInfo.JOB_FINISHED;
        }
    }

    @JobInfo.JobResult
    private int onUploadEvents() {
        if (!airship.getAnalytics().isEnabled()) {
            return JobInfo.JOB_FINISHED;
        }

        if (airship.getChannel().getId() == null) {
            Logger.debug("AnalyticsJobHandler - No channel ID, skipping analytics send.");
            return JobInfo.JOB_FINISHED;
        }

        if (eventManager.uploadEvents(airship)) {
            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_RETRY;
    }

}
