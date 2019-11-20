/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.util.UAStringUtil;

import java.util.List;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
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
     * The maximum length of the named user ID string.
     */
    private static final int MAX_NAMED_USER_ID_LENGTH = 128;

    private final PreferenceDataStore preferenceDataStore;
    private final Object lock = new Object();
    private final JobDispatcher jobDispatcher;
    private final TagGroupRegistrar tagGroupRegistrar;
    private NamedUserJobHandler namedUserJobHandler;
    private AirshipChannel airshipChannel;

    /**
     * Creates a NamedUser.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     * @param tagGroupRegistrar The tag group registrar.
     * @param airshipChannel The airship channel.
     */
    public NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
                     @NonNull TagGroupRegistrar tagGroupRegistrar, @NonNull AirshipChannel airshipChannel) {
        this(context, preferenceDataStore, tagGroupRegistrar, airshipChannel, JobDispatcher.shared(context));
    }

    /**
     * @hide
     */
    @VisibleForTesting
    NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore,
              @NonNull TagGroupRegistrar tagGroupRegistrar, @NonNull AirshipChannel airshipChannel,
              @NonNull JobDispatcher dispatcher) {
        super(context, preferenceDataStore);
        this.preferenceDataStore = preferenceDataStore;
        this.tagGroupRegistrar = tagGroupRegistrar;
        this.airshipChannel = airshipChannel;
        this.jobDispatcher = dispatcher;
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

        // Start named user update
        dispatchNamedUserUpdateJob();

        // Update named user tags if we have a named user
        if (getId() != null) {
            dispatchUpdateTagGroupsJob();
        }
    }

    /**
     * @hide
     */
    @Override
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (namedUserJobHandler == null) {
            namedUserJobHandler = new NamedUserJobHandler(airship, preferenceDataStore, tagGroupRegistrar);
        }

        return namedUserJobHandler.performJob(jobInfo);
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
     * To disassociate the named user ID, its value must be null.
     *
     * @param namedUserId The named user ID string.
     */
    public void setId(@Nullable String namedUserId) {
        String id = null;
        if (namedUserId != null) {
            id = namedUserId.trim();
            if (UAStringUtil.isEmpty(id) || id.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("Failed to set named user ID. The named user ID must be greater than 0 and less than 129 characters.");
                return;
            }
        }

        synchronized (lock) {
            // check if the newly trimmed ID matches with currently stored ID
            boolean isEqual = getId() == null ? id == null : getId().equals(id);

            // if the IDs don't match or ID is set to null and current token is null (re-install case), then update.
            if (!isEqual || (getId() == null && getChangeToken() == null)) {
                preferenceDataStore.put(NAMED_USER_ID_KEY, id);

                // Update the change token.
                updateChangeToken();

                // When named user ID change, clear pending named user tags.
                tagGroupRegistrar.clearMutations(TagGroupRegistrar.NAMED_USER);

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
                if (!collapsedMutations.isEmpty()) {
                    tagGroupRegistrar.addMutations(TagGroupRegistrar.NAMED_USER, collapsedMutations);
                    dispatchUpdateTagGroupsJob();
                }
            }
        };
    }

    /**
     * Gets the named user ID change token.
     *
     * @return The named user ID change token.
     */
    @Nullable
    String getChangeToken() {
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
                                 .setAction(NamedUserJobHandler.ACTION_UPDATE_NAMED_USER)
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
                                 .setAction(NamedUserJobHandler.ACTION_UPDATE_TAG_GROUPS)
                                 .setId(JobInfo.NAMED_USER_UPDATE_TAG_GROUPS)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(NamedUser.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

}
