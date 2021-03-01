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
import com.urbanairship.util.Clock;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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
     * Attribute storage key.
     */
    private static final String ATTRIBUTE_MUTATION_STORE_KEY = "com.urbanairship.nameduser.ATTRIBUTE_MUTATION_STORE_KEY";

    /**
     * Key for storing the pending tag group mutations in the {@link PreferenceDataStore}.
     */
    private static final String TAG_GROUP_MUTATIONS_KEY = "com.urbanairship.nameduser.PENDING_TAG_GROUP_MUTATIONS_KEY";

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
    private final Clock clock;

    private final AirshipChannel airshipChannel;
    private final NamedUserApiClient namedUserApiClient;

    private final TagGroupRegistrar tagGroupRegistrar;
    private final AttributeRegistrar attributeRegistrar;

    private final List<NamedUserListener> namedUserListeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a NamedUser.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     * @param runtimeConfig The airship runtime config.
     * @param airshipChannel The airship channel.
     */
    public NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                     @NonNull AirshipRuntimeConfig runtimeConfig, @NonNull AirshipChannel airshipChannel) {
        this(context, preferenceDataStore, airshipChannel, JobDispatcher.shared(context),
                Clock.DEFAULT_CLOCK, new NamedUserApiClient(runtimeConfig),
                new AttributeRegistrar(AttributeApiClient.namedUserClient(runtimeConfig), new PendingAttributeMutationStore(preferenceDataStore, ATTRIBUTE_MUTATION_STORE_KEY)),
                new TagGroupRegistrar(TagGroupApiClient.namedUserClient(runtimeConfig), new PendingTagGroupMutationStore(preferenceDataStore, TAG_GROUP_MUTATIONS_KEY)));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
              @NonNull AirshipChannel airshipChannel, @NonNull JobDispatcher dispatcher,
              @NonNull Clock clock, @NonNull NamedUserApiClient namedUserApiClient,
              @NonNull AttributeRegistrar attributeRegistrar, @NonNull TagGroupRegistrar tagGroupRegistrar) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.airshipChannel = airshipChannel;
        this.jobDispatcher = dispatcher;
        this.clock = clock;
        this.namedUserApiClient = namedUserApiClient;
        this.attributeRegistrar = attributeRegistrar;
        this.tagGroupRegistrar = tagGroupRegistrar;
    }

    @Override
    protected void init() {
        super.init();

        tagGroupRegistrar.setId(getId(), false);
        attributeRegistrar.setId(getId(), false);

        airshipChannel.addChannelListener(new AirshipChannelListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                dispatchNamedUserUpdateJob();
            }

            @Override
            public void onChannelUpdated(@NonNull String channelId) {

            }
        });

        airshipChannel.addChannelRegistrationPayloadExtender(new AirshipChannel.ChannelRegistrationPayloadExtender() {
            @NonNull
            @Override
            public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                return builder.setNamedUserId(getId());
            }
        });

        if (airshipChannel.getId() != null && (!isIdUpToDate() || getId() != null)) {
            dispatchNamedUserUpdateJob();
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
        if (ACTION_UPDATE_NAMED_USER.equals(jobInfo.getAction())) {
            return onUpdateNamedUser();
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Gets any pending tag updates.
     *
     * @return The list of pending tag updates.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<TagGroupsMutation> getPendingTagUpdates() {
        return tagGroupRegistrar.getPendingMutations();
    }

    /**
     * Gets any pending attribute updates.
     *
     * @return The list of pending attribute updates.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<AttributeMutation> getPendingAttributeUpdates() {
        return attributeRegistrar.getPendingMutations();
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
        Logger.debug("force named user update.");
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
            Logger.debug("Data collection is disabled, ignoring named user association.");
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
                attributeRegistrar.setId(getId(), true);
                tagGroupRegistrar.setId(getId(), true);
                dispatchNamedUserUpdateJob();

                // ID changed, update CRA
                if (id != null) {
                    airshipChannel.updateRegistration();
                }

                for (NamedUserListener listener : namedUserListeners) {
                    listener.onNamedUserIdChanged(id);
                }
            } else {
                Logger.debug("Skipping update. Named user ID trimmed already matches existing named user: %s", getId());
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
                    tagGroupRegistrar.addPendingMutations(collapsedMutations);
                    dispatchNamedUserUpdateJob();
                }
            }
        };
    }

    /**
     * Edit the attributes associated with the named user.
     *
     * @return An {@link AttributeEditor}.
     */
    @NonNull
    public AttributeEditor editAttributes() {
        return new AttributeEditor(clock) {
            @Override
            protected void onApply(@NonNull List<AttributeMutation> mutations) {
                if (!isDataCollectionEnabled()) {
                    Logger.info("Ignore attributes, data opted out.");
                    return;
                }

                if (!mutations.isEmpty()) {
                    attributeRegistrar.addPendingMutations(mutations);
                    dispatchNamedUserUpdateJob();
                }
            }
        };
    }

    /**
     * Adds a tag group listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addTagGroupListener(@NonNull TagGroupListener listener) {
        this.tagGroupRegistrar.addTagGroupListener(listener);
    }

    /**
     * Adds an attribute listener.
     *
     * @param listener The listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addAttributeListener(@NonNull AttributeListener listener) {
        this.attributeRegistrar.addAttributeListener(listener);
    }

    /**
     * Adds a named user listener.
     *
     * @param listener The named user listener.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addNamedUserListener(@NonNull NamedUserListener listener) {
        namedUserListeners.add(listener);
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
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(NamedUser.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    @Override
    protected void onDataCollectionEnabledChanged(boolean isDataCollectionEnabled) {
        if (!isDataCollectionEnabled) {
            attributeRegistrar.clearPendingMutations();
            tagGroupRegistrar.clearPendingMutations();
            setId(null);
        }
    }

    /**
     * Handles named user update job.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    @WorkerThread
    private int onUpdateNamedUser() {
        String channelId = airshipChannel.getId();
        if (UAStringUtil.isEmpty(channelId)) {
            Logger.verbose("The channel ID does not exist. Will retry when channel ID is available.");
            return JobInfo.JOB_FINISHED;
        }

        // Update ID
        if (!isIdUpToDate()) {
            int result = updateNamedUserId(channelId);
            if (result != JobInfo.JOB_FINISHED) {
                return result;
            }
        }

        // Update tag groups and attributes if we have an Id and it's up to date
        if (isIdUpToDate() && getId() != null) {
            boolean attributeResult = attributeRegistrar.uploadPendingMutations();
            boolean tagResult = tagGroupRegistrar.uploadPendingMutations();
            if (!attributeResult || !tagResult) {
                return JobInfo.JOB_RETRY;
            }
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * Handles associate/disassociate updates.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    @WorkerThread
    private int updateNamedUserId(@NonNull String channelId) {
        String changeToken;
        String namedUserId;

        synchronized (idLock) {
            changeToken = getChangeToken();
            namedUserId = getId();
        }

        Response<Void> response;
        try {
            response = namedUserId == null ? namedUserApiClient.disassociate(channelId)
                    : namedUserApiClient.associate(namedUserId, channelId);
        } catch (RequestException e) {
            // Server error occurred, so retry later.
            Logger.debug(e, "Update named user failed, will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 500 | 429
        if (response.isServerError() || response.isTooManyRequestsError()) {
            Logger.debug("Update named user failed. Too many requests. Will retry.");
            return JobInfo.JOB_RETRY;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.debug("Update named user failed with response: %s." +
                    "This action is not allowed when the app is in server-only mode.", response);
            return JobInfo.JOB_FINISHED;
        }

        // 2xx
        if (response.isSuccessful()) {
            Logger.debug("Update named user succeeded with status: %s", response.getStatus());
            preferenceDataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            return JobInfo.JOB_FINISHED;
        }

        // 4xx
        Logger.debug("Update named user failed with response: %s", response);
        return JobInfo.JOB_FINISHED;
    }

}
