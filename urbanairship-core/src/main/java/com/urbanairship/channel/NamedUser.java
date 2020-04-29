/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipComponentGroups;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.config.AirshipRuntimeConfig;
import com.urbanairship.http.RequestException;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.Size;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * The named user is an alternate method of identifying the device. Once a named
 * user is associated to the device, it can be used to send push notifications
 * to the device.
 */
public class NamedUser extends AirshipComponent {

    /**
     * The change token tracks the start of setting the named user ID.
     */
    private static final String CHANGE_TOKEN_KEY = "com.urbanairship.nameduser.CHANGE_TOKEN_KEY";

    /**
     * The named user ID.
     */
    private static final String NAMED_USER_ID_KEY = "com.urbanairship.nameduser.NAMED_USER_ID_KEY";

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
    private static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    /**
     * The maximum length of the named user ID string.
     */
    private static final int MAX_NAMED_USER_ID_LENGTH = 128;

    private final PreferenceDataStore preferenceDataStore;
    private final Object idLock = new Object();
    private final JobDispatcher jobDispatcher;
    private final TagGroupRegistrar tagGroupRegistrar;

    private final AirshipChannel airshipChannel;
    private final NamedUserApiClient namedUserApiClient;

    /**
     * Creates a NamedUser.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     * @param runtimeConfig The airship runtime config.
     * @param tagGroupRegistrar The tag group registrar.
     * @param airshipChannel The airship channel.
     */
    public NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                     @NonNull AirshipRuntimeConfig runtimeConfig, @NonNull TagGroupRegistrar tagGroupRegistrar,
                     @NonNull AirshipChannel airshipChannel) {
        this(context, preferenceDataStore, tagGroupRegistrar, airshipChannel, JobDispatcher.shared(context),
                new NamedUserApiClient(runtimeConfig));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
              @NonNull TagGroupRegistrar tagGroupRegistrar, @NonNull AirshipChannel airshipChannel,
              @NonNull JobDispatcher dispatcher, @NonNull NamedUserApiClient namedUserApiClient) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.tagGroupRegistrar = tagGroupRegistrar;
        this.airshipChannel = airshipChannel;
        this.jobDispatcher = dispatcher;
        this.namedUserApiClient = namedUserApiClient;

    }

    @Override
    protected void init() {
        super.init();

        airshipChannel.addChannelListener(new AirshipChannelListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                dispatchNamedUserUpdateJob();
            }

            @Override
            public void onChannelUpdated(@NonNull String channelId) {

            }
        });

        if (!isIdUpToDate()) {
            dispatchNamedUserUpdateJob();
        } else if (getId() != null) {
            dispatchUpdateTagGroupsJob();
        }
    }

    /**
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @AirshipComponentGroups.Group
    public int getComponentGroup() {
        return AirshipComponentGroups.NAMED_USER;
    }

    /**
     * @hide
     */
    @Override
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        switch (jobInfo.getAction()) {
            case ACTION_UPDATE_NAMED_USER:
                return onUpdateNamedUser();

            case ACTION_UPDATE_TAG_GROUPS:
                return onUpdateTagGroup();
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Returns the named user ID.
     *
     * @return The named user ID as a string or null if it does not exist.
     */
    @Nullable
    public String getId() {
        return preferenceDataStore.getString(NAMED_USER_ID_KEY, null);
    }

    /**
     * Forces a named user update.
     */
    public void forceUpdate() {
        Logger.debug("NamedUser - force named user update.");
        updateChangeToken();
        dispatchNamedUserUpdateJob();
    }

    /**
     * Sets the named user ID.
     * <p>
     * To associate the named user ID, its length must be greater than 0 and less than 129 characters.
     * To disassociate the named user ID, its value must be empty or null.
     *
     * @param namedUserId The named user ID string.
     */
    public void setId(@Nullable @Size(max = MAX_NAMED_USER_ID_LENGTH) String namedUserId) {
        if (namedUserId != null && !isDataCollectionEnabled()) {
            Logger.debug("NamedUser - Data collection is disabled, ignoring named user association.");
            return;
        }

        String id = null;

        // Treat empty namedUserId as a command to dissociate
        if (!UAStringUtil.isEmpty(namedUserId)) {
            id = namedUserId.trim();

            // Treat namedUserId trimmed to empty as invalid
            if (UAStringUtil.isEmpty(id) || id.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("Failed to set named user ID. The named user ID must be composed" +
                        "of non-whitespace characters and be less than 129 characters in length.");
                return;
            }
        }

        synchronized (idLock) {
            if (!UAStringUtil.equals(getId(), id)) {
                // New/Cleared Named User, clear pending updates and update the token and ID
                preferenceDataStore.put(NAMED_USER_ID_KEY, id);
                updateChangeToken();
                clearPendingNamedUserUpdates();

                dispatchNamedUserUpdateJob();
            } else {
                Logger.debug("NamedUser - Skipping update. Named user ID trimmed already matches existing named user: %s", getId());
            }
        }
    }

    /**
     * Edit the named user tags.
     *
     * @return The TagGroupsEditor.
     */
    @NonNull
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor() {
            @Override
            protected void onApply(@NonNull List<TagGroupsMutation> collapsedMutations) {
                if (!isDataCollectionEnabled()) {
                    Logger.warn("NamedUser - Unable to apply tag group edits when data collection is disabled.");
                    return;
                }

                if (!collapsedMutations.isEmpty()) {
                    tagGroupRegistrar.addMutations(TagGroupRegistrar.NAMED_USER, collapsedMutations);
                    dispatchUpdateTagGroupsJob();
                }
            }
        };
    }

    @VisibleForTesting
    boolean isIdUpToDate() {
        synchronized (idLock) {
            String changeToken = getChangeToken();
            String lastUpdatedToken = preferenceDataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
            String currentId = getId();

            if (currentId == null && changeToken == null) {
                return true;
            }

            return lastUpdatedToken != null && lastUpdatedToken.equals(changeToken);
        }
    }

    /**
     * Gets the named user ID change token.
     *
     * @return The named user ID change token.
     */
    @Nullable
    private String getChangeToken() {
        return preferenceDataStore.getString(CHANGE_TOKEN_KEY, null);
    }

    /**
     * Modify the change token to force an update.
     */
    private void updateChangeToken() {
        preferenceDataStore.put(CHANGE_TOKEN_KEY, UUID.randomUUID().toString());
    }

    /**
     * Dispatches a job to update the named user.
     */
    void dispatchNamedUserUpdateJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_NAMED_USER)
                                 .setId(JobInfo.NAMED_USER_UPDATE_ID)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(NamedUser.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * Dispatches a job to update the named user tag groups.
     */
    void dispatchUpdateTagGroupsJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_TAG_GROUPS)
                                 .setId(JobInfo.NAMED_USER_UPDATE_TAG_GROUPS)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(NamedUser.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    private void clearPendingNamedUserUpdates() {
        Logger.verbose("Clearing pending Named Users tag updates.");
        tagGroupRegistrar.clearMutations(TagGroupRegistrar.NAMED_USER);
    }

    @Override
    protected void onDataCollectionEnabledChanged(boolean isDataCollectionEnabled) {
        if (!isDataCollectionEnabled) {
            clearPendingNamedUserUpdates();
            setId(null);
        }
    }

    /**
     * Handles associate/disassociate updates.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    @WorkerThread
    private int onUpdateNamedUser() {
        String changeToken;
        String currentId;

        synchronized (idLock) {
            if (isIdUpToDate()) {
                Logger.verbose("NamedUserJobHandler - Named user already updated. Skipping.");
                return JobInfo.JOB_FINISHED;
            }

            changeToken = getChangeToken();
            currentId = getId();
        }

        String channelId = airshipChannel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.info("The channel ID does not exist. Will retry when channel ID is available.");
            return JobInfo.JOB_FINISHED;
        }

        Response<Void> response;
        try {
            response = currentId == null ? namedUserApiClient.disassociate(channelId)
                    : namedUserApiClient.associate(currentId, channelId);
        } catch (RequestException e) {
            // Server error occurred, so retry later.
            Logger.debug(e, "NamedUser - Update named user failed, will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 500 | 429
        if (response.isServerError() || response.isTooManyRequestsError()) {
            Logger.debug("Update named user failed. Too many requests. Will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 2xx
        if (response.isSuccessful()) {
            Logger.debug("Update named user succeeded with status: %s", response.getStatus());
            preferenceDataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            dispatchUpdateTagGroupsJob();
            return JobInfo.JOB_FINISHED;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.debug("Update named user failed with response: %s." +
                    "This action is not allowed when the app is in server-only mode.", response);
            return JobInfo.JOB_FINISHED;
        }

        // 4xx
        Logger.debug("Update named user failed with response: %s", response);
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    @WorkerThread
    private int onUpdateTagGroup() {
        String namedUserId = getId();
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
