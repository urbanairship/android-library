/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.content.Context;
import android.provider.Settings;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.data.EventManager;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;

/**
 * Handles intents for {@link Analytics#onPerformJob(UAirship, JobInfo)}.
 */
class AnalyticsJobHandler {


    private final Context context;
    private final UAirship airship;
    private final EventManager eventManager;


    AnalyticsJobHandler(Context context, UAirship airship, EventManager eventManager) {
        this.airship = airship;
        this.context = context;
        this.eventManager = eventManager;
    }


    @JobInfo.JobResult
    public int performJob(JobInfo jobInfo) {
        Logger.verbose("AnalyticsJobHandler - Received jobInfo with action: " + jobInfo.getAction());

        switch (jobInfo.getAction()) {
            case Analytics.ACTION_SEND:
                return onUploadEvents();

            case Analytics.ACTION_UPDATE_ADVERTISING_ID:
                return onUpdateAdvertisingId();

            default:
                Logger.warn("AnalyticsJobHandler - Unrecognized jobInfo with action: " + jobInfo.getAction());
                return JobInfo.JOB_FINISHED;
        }
    }

    @JobInfo.JobResult
    private int onUploadEvents() {
        if (!airship.getAnalytics().isEnabled()) {
            return JobInfo.JOB_FINISHED;
        }

        if (airship.getPushManager().getChannelId() == null) {
            Logger.debug("AnalyticsJobHandler - No channel ID, skipping analytics send.");
            return JobInfo.JOB_FINISHED;
        }

        if (eventManager.uploadEvents(airship)) {
            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_RETRY;
    }

    /**
     * Updates the advertising ID and limited ad tracking preference.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateAdvertisingId() {
        AssociatedIdentifiers associatedIdentifiers = airship.getAnalytics().getAssociatedIdentifiers();

        String advertisingId = associatedIdentifiers.getAdvertisingId();
        boolean limitedAdTrackingEnabled = associatedIdentifiers.isLimitAdTrackingEnabled();


        switch (airship.getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                advertisingId = Settings.Secure.getString(context.getContentResolver(), "advertising_id");
                limitedAdTrackingEnabled = Settings.Secure.getInt(context.getContentResolver(), "limit_ad_tracking", -1) == 0;
                break;

            case UAirship.ANDROID_PLATFORM:
                if (!PlayServicesUtils.isGoogleAdsDependencyAvailable()) {
                    break;
                }

                try {
                    AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(context);
                    if (adInfo == null) {
                        break;
                    }

                    advertisingId = adInfo.getId();
                    limitedAdTrackingEnabled = adInfo.isLimitAdTrackingEnabled();
                } catch (IOException | GooglePlayServicesNotAvailableException | GooglePlayServicesRepairableException e) {
                    Logger.error("AnalyticsJobHandler - Failed to retrieve and update advertising ID.", e);
                    return JobInfo.JOB_RETRY;
                }

                break;
        }

        if (!UAStringUtil.equals(associatedIdentifiers.getAdvertisingId(), advertisingId) ||
                associatedIdentifiers.isLimitAdTrackingEnabled() != limitedAdTrackingEnabled) {

            airship.getAnalytics().editAssociatedIdentifiers()
                   .setAdvertisingId(advertisingId, limitedAdTrackingEnabled)
                   .apply();
        }

        return JobInfo.JOB_FINISHED;

    }
}
