/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;


/**
 * Job handler for the NamedUser.
 */
class NamedUserJobHandler {

    /**
     * Action to perform update request for pending named user tag group changes.
     */
    static final String ACTION_UPDATE_TAG_GROUPS = "ACTION_UPDATE_TAG_GROUPS";

    /**
     * Action to update named user association or disassociation.
     */
    static final String ACTION_UPDATE_NAMED_USER = "ACTION_UPDATE_NAMED_USER";

    /**
     * Key for storing the {@link NamedUser#getChangeToken()} in the {@link PreferenceDataStore} from the
     * last time the named user was updated.
     */
    static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    private final NamedUserApiClient client;
    private final NamedUser namedUser;
    private final PushManager pushManager;
    private final PreferenceDataStore dataStore;

    /**
     * Default constructor.
     *
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    NamedUserJobHandler(UAirship airship, PreferenceDataStore dataStore) {
        this(airship, dataStore, new NamedUserApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    NamedUserJobHandler(UAirship airship, PreferenceDataStore dataStore,  NamedUserApiClient client) {
        this.dataStore = dataStore;
        this.client = client;
        this.namedUser = airship.getNamedUser();
        this.pushManager = airship.getPushManager();
    }

    /**
     * Called to handle jobs from {@link NamedUser#onPerformJob(UAirship, Job)}.
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    @JobInfo.JobResult
    protected int performJob(JobInfo jobInfo) {
        switch (jobInfo.getAction()) {
            case ACTION_UPDATE_NAMED_USER:
                return onUpdateNamedUser();

            case ACTION_UPDATE_TAG_GROUPS:
                return onUpdateTagGroup();
        }

        return JobInfo.JOB_FINISHED;
    }


    /**
     * Handles associate/disassociate updates.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateNamedUser() {
        String currentId = namedUser.getId();
        String changeToken = namedUser.getChangeToken();
        String lastUpdatedToken = dataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
        String channelId = pushManager.getChannelId();

        if (changeToken == null && lastUpdatedToken == null) {
            // Skip since no one has set the named user ID. Usually from a new or re-install.
            return JobInfo.JOB_FINISHED;
        }

        if (changeToken != null && changeToken.equals(lastUpdatedToken)) {
            // Skip since no change has occurred (token remain the same).
            Logger.debug("NamedUserJobHandler - Named user already updated. Skipping.");
            return JobInfo.JOB_FINISHED;
        }

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.info("The channel ID does not exist. Will retry when channel ID is available.");
            return JobInfo.JOB_FINISHED;
        }

        Response response;

        if (currentId == null) {
            // When currentId is null, disassociate the current named user ID.
            response = client.disassociate(channelId);
        } else {
            // When currentId is non-null, associate the currentId.
            response = client.associate(currentId, channelId);
        }

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.info("Update named user failed, will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update named user succeeded with status: " + response.getStatus());
            dataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            namedUser.dispatchUpdateTagGroupsJob();
            return JobInfo.JOB_FINISHED;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.info("Update named user failed with status: " + response.getStatus() +
                    " This action is not allowed when the app is in server-only mode.");
            return JobInfo.JOB_FINISHED;
        }

        // 4xx
        Logger.info("Update named user failed with status: " + response.getStatus());
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateTagGroup() {
        String namedUserId = namedUser.getId();
        if (namedUserId == null) {
            Logger.verbose("Failed to update named user tags due to null named user ID.");
            return JobInfo.JOB_FINISHED;
        }

        TagGroupsMutation mutation;
        while ((mutation = namedUser.getTagGroupStore().pop()) != null) {
            Response response = client.updateTagGroups(namedUserId, mutation);

            // 5xx or no response
            if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
                Logger.info("NamedUserJobHandler - Failed to update tag groups, will retry later.");
                namedUser.getTagGroupStore().push(mutation);
                return JobInfo.JOB_RETRY;
            }

            int status = response.getStatus();
            Logger.info("NamedUserJobHandler - Update tag groups finished with status: " + status);
        }

        return JobInfo.JOB_FINISHED;
    }
}
