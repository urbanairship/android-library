/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.List;

import static com.urbanairship.push.TagUtils.migrateTagGroups;


/**
 * Job handler for the NamedUser.
 */
class NamedUserJobHandler {

    /**
     * Action to update pending named user tag groups.
     */
    static final String ACTION_APPLY_TAG_GROUP_CHANGES = "com.urbanairship.nameduser.ACTION_APPLY_TAG_GROUP_CHANGES";

    /**
     * Action to perform update request for pending named user tag group changes.
     */
    static final String ACTION_UPDATE_TAG_GROUPS = "com.urbanairship.nameduser.ACTION_UPDATE_TAG_GROUPS";

    /**
     * Key for storing the pending named user add tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_ADD_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_ADD_TAG_GROUPS_KEY";

    /**
     * Key for storing the pending named user remove tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_REMOVE_TAG_GROUPS_KEY";

    /**
     * Key for storing the pending tag group mutations in the {@link PreferenceDataStore}.
     */
    static final String PENDING_TAG_GROUP_MUTATIONS_KEY = "com.urbanairship.nameduser.PENDING_TAG_GROUP_MUTATIONS_KEY";

    /**
     * Action to update named user association or disassociation.
     */
    static final String ACTION_UPDATE_NAMED_USER = "com.urbanairship.push.ACTION_UPDATE_NAMED_USER";

    /**
     * Key for storing the {@link NamedUser#getChangeToken()} in the {@link PreferenceDataStore} from the
     * last time the named user was updated.
     */
    static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    /**
     * Action to clear the pending named user tags.
     */
    static final String ACTION_CLEAR_PENDING_NAMED_USER_TAGS = "com.urbanairship.nameduser.ACTION_CLEAR_PENDING_NAMED_USER_TAGS";

    private final NamedUserApiClient client;

    private final NamedUser namedUser;
    private final PushManager pushManager;
    private final PreferenceDataStore dataStore;
    private final JobDispatcher jobDispatcher;



    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    NamedUserJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(airship, dataStore, JobDispatcher.shared(context), new NamedUserApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    NamedUserJobHandler(UAirship airship, PreferenceDataStore dataStore, JobDispatcher jobDispatcher, NamedUserApiClient client) {
        this.dataStore = dataStore;
        this.client = client;
        this.namedUser = airship.getNamedUser();
        this.pushManager = airship.getPushManager();
        this.jobDispatcher = jobDispatcher;
    }

    /**
     * Called to handle jobs from {@link NamedUser#onPerformJob(UAirship, Job)}.
     *
     * @param job The airship job.
     * @return The job result.
     */
    @Job.JobResult
    protected int performJob(Job job) {
        switch (job.getAction()) {
            case ACTION_UPDATE_NAMED_USER:
                return onUpdateNamedUser();

            case ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
                return onClearTagGroups();

            case ACTION_APPLY_TAG_GROUP_CHANGES:
                return onApplyTagGroupChanges(job);

            case ACTION_UPDATE_TAG_GROUPS:
                return onUpdateTagGroup();
        }

        return Job.JOB_FINISHED;
    }


    /**
     * Handles associate/disassociate updates.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onUpdateNamedUser() {
        String currentId = namedUser.getId();
        String changeToken = namedUser.getChangeToken();
        String lastUpdatedToken = dataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
        String channelId = pushManager.getChannelId();

        if (changeToken == null && lastUpdatedToken == null) {
            // Skip since no one has set the named user ID. Usually from a new or re-install.
            return Job.JOB_FINISHED;
        }

        if (changeToken != null && changeToken.equals(lastUpdatedToken)) {
            // Skip since no change has occurred (token remain the same).
            Logger.debug("NamedUserJobHandler - Named user already updated. Skipping.");
            return Job.JOB_FINISHED;
        }

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.info("The channel ID does not exist. Will retry when channel ID is available.");
            return Job.JOB_FINISHED;
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
            return Job.JOB_RETRY;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update named user succeeded with status: " + response.getStatus());
            dataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            namedUser.dispatchUpdateTagGroupsJob();
            return Job.JOB_FINISHED;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.info("Update named user failed with status: " + response.getStatus() +
                    " This action is not allowed when the app is in server-only mode.");
            return Job.JOB_FINISHED;
        }

        // 4xx
        Logger.info("Update named user failed with status: " + response.getStatus());
        return Job.JOB_FINISHED;
    }

    /**
     * Handles any pending tag group changes.
     *
     * @param job The airship job.
     * @return The job result.
     */
    @Job.JobResult
    private int onApplyTagGroupChanges(Job job) {
        String namedUserId = namedUser.getId();
        if (namedUserId == null) {
            Logger.verbose("Failed to update named user tags due to null named user ID.");
            return Job.JOB_FINISHED;
        }

        migrateTagGroups(dataStore, PENDING_ADD_TAG_GROUPS_KEY, PENDING_REMOVE_TAG_GROUPS_KEY, PENDING_TAG_GROUP_MUTATIONS_KEY);

        List<TagGroupsMutation> mutations = TagGroupsMutation.fromJsonList(dataStore.getJsonValue(PENDING_TAG_GROUP_MUTATIONS_KEY).optList());
        try {
            JsonValue jsonValue = JsonValue.parseString(job.getExtras().getString(TagGroupsEditor.EXTRA_TAG_GROUP_MUTATIONS));
            mutations.addAll(TagGroupsMutation.fromJsonList(jsonValue.optList()));
        } catch (JsonException e) {
            Logger.error("Failed to parse tag group change:", e);
            return Job.JOB_FINISHED;
        }

        mutations = TagGroupsMutation.collapseMutations(mutations);
        dataStore.put(PENDING_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(mutations));


        Job updateJob = Job.newBuilder()
                           .setAction(ACTION_UPDATE_TAG_GROUPS)
                           .setTag(ACTION_UPDATE_TAG_GROUPS)
                           .setNetworkAccessRequired(true)
                           .setAirshipComponent(NamedUser.class)
                           .build();

        jobDispatcher.dispatch(updateJob);

        return Job.JOB_FINISHED;
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onUpdateTagGroup() {
        migrateTagGroups(dataStore, PENDING_ADD_TAG_GROUPS_KEY, PENDING_REMOVE_TAG_GROUPS_KEY, PENDING_TAG_GROUP_MUTATIONS_KEY);

        String namedUserId = namedUser.getId();
        if (namedUserId == null) {
            Logger.verbose("Failed to update named user tags due to null named user ID.");
            return Job.JOB_FINISHED;
        }

        List<TagGroupsMutation> mutations = TagGroupsMutation.fromJsonList(dataStore.getJsonValue(PENDING_TAG_GROUP_MUTATIONS_KEY).optList());

        if (mutations.isEmpty()) {
            Logger.verbose( "NamedUserJobHandler - No pending tag group updates. Skipping update.");
            return Job.JOB_FINISHED;
        }

        Response response = client.updateTagGroups(namedUserId, mutations.get(0));

        // 5xx or no response
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.info("NamedUserJobHandler - Failed to update tag groups, will retry later.");
            return Job.JOB_RETRY;
        }

        int status = response.getStatus();
        Logger.info("NamedUserJobHandler - Update tag groups finished with status: " + status);
        if (UAHttpStatusUtil.inSuccessRange(status) || status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_BAD_REQUEST) {
            mutations.remove(0);

            if (mutations.isEmpty()) {
                dataStore.remove(PENDING_TAG_GROUP_MUTATIONS_KEY);
            } else {
                dataStore.put(PENDING_TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(mutations));
            }


            if (!mutations.isEmpty()) {
                Job updateJob = Job.newBuilder()
                                   .setAction(ACTION_UPDATE_TAG_GROUPS)
                                   .setTag(ACTION_UPDATE_TAG_GROUPS)
                                   .setNetworkAccessRequired(true)
                                   .setAirshipComponent(PushManager.class)
                                   .build();

                jobDispatcher.dispatch(updateJob);
            }
        }

        return Job.JOB_FINISHED;
    }

    /**
     * Handles clearing pending tag groups.
     *
     * @return The job result.
     */
    @Job.JobResult
    private int onClearTagGroups() {
        dataStore.remove(PENDING_ADD_TAG_GROUPS_KEY);
        dataStore.remove(PENDING_REMOVE_TAG_GROUPS_KEY);
        dataStore.remove(PENDING_TAG_GROUP_MUTATIONS_KEY);

        return Job.JOB_FINISHED;
    }
}
