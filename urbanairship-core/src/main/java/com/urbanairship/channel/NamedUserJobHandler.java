/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.push.PushManager;
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
    private final AirshipChannel channel;
    private final PreferenceDataStore dataStore;
    private final TagGroupRegistrar tagGroupRegistrar;

    /**
     * Default constructor.
     *
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    NamedUserJobHandler(@NonNull UAirship airship, @NonNull PreferenceDataStore dataStore, @NonNull TagGroupRegistrar tagGroupRegistrar) {
        this(airship, dataStore, tagGroupRegistrar, new NamedUserApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    NamedUserJobHandler(@NonNull UAirship airship, @NonNull PreferenceDataStore dataStore, @NonNull TagGroupRegistrar tagGroupRegistrar, @NonNull NamedUserApiClient client) {
        this.dataStore = dataStore;
        this.client = client;
        this.namedUser = airship.getNamedUser();
        this.channel = airship.getChannel();
        this.tagGroupRegistrar = tagGroupRegistrar;
    }

    /**
     * Called to handle jobs from {@link NamedUser#onPerformJob(UAirship, JobInfo)}.
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    @JobInfo.JobResult
    protected int performJob(@NonNull JobInfo jobInfo) {
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
        String channelId = channel.getId();

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
            Logger.debug("Update named user failed, will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 429
        if (response.getStatus() == Response.HTTP_TOO_MANY_REQUESTS) {
            Logger.debug("Update named user failed. Too many requests. Will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.debug("Update named user succeeded with status: %s", response.getStatus());
            dataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            namedUser.dispatchUpdateTagGroupsJob();
            return JobInfo.JOB_FINISHED;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.debug("Update named user failed with status: %s. This action is not allowed when the app is in server-only mode.", response.getStatus());
            return JobInfo.JOB_FINISHED;
        }

        // 4xx
        Logger.debug("Update named user failed with status: %s", response.getStatus());
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

        if (tagGroupRegistrar.uploadMutations(TagGroupRegistrar.NAMED_USER, namedUserId)) {
            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_RETRY;
    }

}
