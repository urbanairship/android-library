/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import android.content.Context;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.locale.LocaleChangedListener;
import com.urbanairship.locale.LocaleManager;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

/**
 * Airship channel access.
 */
public class AirshipChannel extends AirshipComponent {

    /**
     * Action to perform update request for pending tag group changes.
     */
    private static final String ACTION_UPDATE_TAG_GROUPS = "ACTION_UPDATE_TAG_GROUPS";

    /**
     * Action to perform update request for pending attribute changes.
     */
    private static final String ACTION_UPDATE_ATTRIBUTES = "ACTION_UPDATE_ATTRIBUTES";

    /**
     * Action to update channel registration.
     */
    private static final String ACTION_UPDATE_CHANNEL_REGISTRATION = "ACTION_UPDATE_CHANNEL_REGISTRATION";

    /**
     * Max time between channel registration updates.
     */
    private static final long CHANNEL_REREGISTRATION_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    /**
     * The default tag group.
     */
    private final String DEFAULT_TAG_GROUP = "device";

    // PreferenceDataStore keys
    private static final String APID_KEY = "com.urbanairship.push.APID";
    private static final String CHANNEL_ID_KEY = "com.urbanairship.push.CHANNEL_ID";
    private static final String TAGS_KEY = "com.urbanairship.push.TAGS";
    private static final String LAST_REGISTRATION_TIME_KEY = "com.urbanairship.push.LAST_REGISTRATION_TIME";
    private static final String LAST_REGISTRATION_PAYLOAD_KEY = "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD";
    private static final String ATTRIBUTE_DATASTORE_KEY = "com.urbanairship.push.ATTRIBUTE_DATA_STORE";

    private final AirshipConfigOptions configOptions;
    private final ChannelApiClient channelApiClient;
    private final AttributeApiClient attributeApiClient;
    private final TagGroupRegistrar tagGroupRegistrar;
    private final JobDispatcher jobDispatcher;
    private final LocaleManager localeManager;

    @UAirship.Platform
    private final int platform;
    private final List<AirshipChannelListener> airshipChannelListeners = new CopyOnWriteArrayList<>();
    private final List<ChannelRegistrationPayloadExtender> channelRegistrationPayloadExtenders = new CopyOnWriteArrayList<>();
    private final Object tagLock = new Object();
    private final Object attributeLock = new Object();

    private final PendingAttributeMutationStore attributeMutationStore;

    private boolean channelTagRegistrationEnabled = true;
    private boolean channelCreationDelayEnabled;

    /**
     * Channel registration extender.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface ChannelRegistrationPayloadExtender {

        /**
         * Extends the channel registration builder.
         *
         * @param builder The builder.
         * @return The extended builder.
         */
        @WorkerThread
        @NonNull
        ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder);

    }

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @param configOptions The config options.
     * @param platform The current platform.
     * @param tagGroupRegistrar The tag group registrar.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AirshipChannel(@NonNull Context context,
                          @NonNull PreferenceDataStore dataStore,
                          @NonNull AirshipConfigOptions configOptions,
                          @UAirship.Platform int platform,
                          @NonNull TagGroupRegistrar tagGroupRegistrar) {
        this(context, dataStore, configOptions, new ChannelApiClient(configOptions),
                tagGroupRegistrar, platform, LocaleManager.shared(context), JobDispatcher.shared(context),
                new PendingAttributeMutationStore(dataStore, ATTRIBUTE_DATASTORE_KEY), new AttributeApiClient(platform, configOptions));
    }

    @VisibleForTesting
    AirshipChannel(@NonNull Context context,
                   @NonNull PreferenceDataStore dataStore,
                   @NonNull AirshipConfigOptions configOptions,
                   @NonNull ChannelApiClient channelApiClient,
                   @NonNull TagGroupRegistrar tagGroupRegistrar,
                   @UAirship.Platform int platform,
                   @NonNull LocaleManager localeManager,
                   @NonNull JobDispatcher jobDispatcher,
                   @NonNull PendingAttributeMutationStore attributeMutationStore,
                   @NonNull AttributeApiClient attributeApiClient) {
        super(context, dataStore);

        this.configOptions = configOptions;
        this.channelApiClient = channelApiClient;
        this.tagGroupRegistrar = tagGroupRegistrar;
        this.platform = platform;
        this.localeManager = localeManager;
        this.jobDispatcher = jobDispatcher;
        this.attributeMutationStore = attributeMutationStore;
        this.attributeApiClient = attributeApiClient;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void init() {
        super.init();
        if (Logger.getLogLevel() < Log.ASSERT && !UAStringUtil.isEmpty(getId())) {
            Log.d(UAirship.getAppName() + " Channel ID", getId());
        }

        channelCreationDelayEnabled = getId() == null && configOptions.channelCreationDelayEnabled;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    protected void onAirshipReady(@NonNull UAirship airship) {
        super.onAirshipReady(airship);

        localeManager.addListener(new LocaleChangedListener() {
            @Override
            public void onLocaleChanged(@NonNull Locale locale) {
                dispatchUpdateRegistrationJob();
            }
        });

        if (getId() != null) {
            dispatchUpdateRegistrationJob();
            dispatchUpdateTagGroupsJob();
            dispatchUpdateAttributesJob();
        } else if (!channelCreationDelayEnabled) {
            dispatchUpdateRegistrationJob();
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void addChannelRegistrationPayloadExtender(@NonNull ChannelRegistrationPayloadExtender extender) {
        this.channelRegistrationPayloadExtenders.add(extender);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @WorkerThread
    @JobInfo.JobResult
    @Override
    public int onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        switch (jobInfo.getAction()) {
            case ACTION_UPDATE_CHANNEL_REGISTRATION:
                ChannelRegistrationPayload payload = getNextChannelRegistrationPayload();
                String channelId = getId();

                if (channelId == null && channelCreationDelayEnabled) {
                    Logger.debug("AirshipChannel - Channel registration is currently disabled.");
                    return JobInfo.JOB_FINISHED;
                }

                if (!shouldUpdateRegistration(payload)) {
                    Logger.verbose("AirshipChannel - Channel already up to date.");
                    return JobInfo.JOB_FINISHED;
                }

                Logger.verbose("AirshipChannel - Performing channel registration.");

                if (UAStringUtil.isEmpty(channelId)) {
                    return onCreateChannel();
                } else {
                    return onUpdateChannel();
                }

            case ACTION_UPDATE_TAG_GROUPS:
                return onUpdateTagGroup();

            case ACTION_UPDATE_ATTRIBUTES:
                return onUpdateAttributes();
        }

        return JobInfo.JOB_FINISHED;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void onComponentEnableChange(boolean isEnabled) {
        if (isEnabled) {
            dispatchUpdateRegistrationJob();
            dispatchUpdateTagGroupsJob();
            dispatchUpdateAttributesJob();
        }
    }

    /**
     * Gets the channel identifier. This Id is created asynchronously, so initially it may be null.
     * To be notified when the channel is updated, add a listener with {@link #addChannelListener(AirshipChannelListener)}.
     *
     * @return The channel Id, or null if the Id is not yet created.
     */
    @Nullable
    public String getId() {
        return getDataStore().getString(CHANNEL_ID_KEY, null);
    }

    /**
     * Adds a channel listener.
     *
     * @param listener The listener.
     */
    public void addChannelListener(@NonNull AirshipChannelListener listener) {
        this.airshipChannelListeners.add(listener);
    }

    /**
     * Removes a channel listener.
     *
     * @param listener The listener.
     */
    public void removeChannelListener(@NonNull AirshipChannelListener listener) {
        this.airshipChannelListeners.remove(listener);
    }

    /**
     * Edits channel Tags.
     *
     * @return A {@link TagEditor}
     */
    @NonNull
    public TagEditor editTags() {
        return new TagEditor() {
            @Override
            void onApply(boolean clear, @NonNull Set<String> tagsToAdd, @NonNull Set<String> tagsToRemove) {
                synchronized (tagLock) {
                    Set<String> tags = clear ? new HashSet<String>() : getTags();
                    tags.addAll(tagsToAdd);
                    tags.removeAll(tagsToRemove);
                    setTags(tags);
                }
            }
        };
    }

    /**
     * Edit the channel tag groups.
     *
     * @return A {@link TagGroupsEditor}.
     */
    @NonNull
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor() {
            @Override
            protected boolean allowTagGroupChange(@NonNull String tagGroup) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to add tags to `%s` tag group when `channelTagRegistrationEnabled` is true.", DEFAULT_TAG_GROUP);
                    return false;
                }

                return true;
            }

            @Override
            protected void onApply(@NonNull List<TagGroupsMutation> collapsedMutations) {
                if (collapsedMutations.isEmpty()) {
                    return;
                }

                tagGroupRegistrar.addMutations(TagGroupRegistrar.CHANNEL, collapsedMutations);
                dispatchUpdateTagGroupsJob();
            }
        };
    }



    /**
     * Edit the attributes associated with this channel.
     *
     * @return An {@link AttributeEditor}.
     */
    @NonNull
    public AttributeEditor editAttributes() {
        return new AttributeEditor() {
            @Override
            protected void onApply(@NonNull List<AttributeMutation> mutations) {
                synchronized (attributeLock) {
                    List<PendingAttributeMutation> pendingMutations = PendingAttributeMutation.fromAttributeMutations(mutations, System.currentTimeMillis());

                    // Add mutations to store
                    attributeMutationStore.add(pendingMutations);
                    dispatchUpdateAttributesJob();
                }
            }
        };
    }

    /**
     * Set tags for the channel and update the server.
     * <p>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p>
     * To clear the current set of tags, pass an empty set to this method.
     *
     * @param tags A set of tag strings.
     */
    public void setTags(@NonNull Set<String> tags) {
        synchronized (tagLock) {
            Set<String> normalizedTags = TagUtils.normalizeTags(tags);
            getDataStore().put(TAGS_KEY, JsonValue.wrapOpt(normalizedTags));
        }

        dispatchUpdateRegistrationJob();
    }

    /**
     * Returns the current set of tags.
     * <p>
     * An empty set indicates that no tags are set on this channel.
     *
     * @return The current set of tags.
     */
    @NonNull
    public Set<String> getTags() {
        synchronized (tagLock) {
            Set<String> tags = new HashSet<>();
            JsonValue jsonValue = getDataStore().getJsonValue(TAGS_KEY);

            if (jsonValue.isJsonList()) {
                for (JsonValue tag : jsonValue.optList()) {
                    if (tag.isString()) {
                        tags.add(tag.getString());
                    }
                }
            }

            Set<String> normalizedTags = TagUtils.normalizeTags(tags);

            // To prevent the getTags call from constantly logging tag set failures, sync tags
            if (tags.size() != normalizedTags.size()) {
                this.setTags(normalizedTags);
            }

            return normalizedTags;
        }
    }

    /**
     * Enables channel creation if channel creation has been delayed.
     * <p>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when channelCreationDelayEnabled has been
     * set to <code>true</code> in the airship config.
     */
    public void enableChannelCreation() {
        if (isChannelCreationDelayEnabled()) {
            channelCreationDelayEnabled = false;
            dispatchUpdateRegistrationJob();
        }
    }

    /**
     * Updates registration.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void updateRegistration() {
        dispatchUpdateRegistrationJob();
    }

    /**
     * Returns the payload for the next channel registration
     *
     * @return The ChannelRegistrationPayload payload
     */
    @WorkerThread
    @NonNull
    private ChannelRegistrationPayload getNextChannelRegistrationPayload() {
        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder()
                .setTags(getChannelTagRegistrationEnabled(), getTags())
                .setUserId(UAirship.shared().getInbox().getUser().getId())
                .setApid(getDataStore().getString(APID_KEY, null));

        switch (platform) {
            case UAirship.ANDROID_PLATFORM:
                builder.setDeviceType("android");
                break;
            case UAirship.AMAZON_PLATFORM:
                builder.setDeviceType("amazon");
                break;
        }

        builder.setTimezone(TimeZone.getDefault().getID());

        Locale locale = localeManager.getDefaultLocale();

        if (!UAStringUtil.isEmpty(locale.getCountry())) {
            builder.setCountry(locale.getCountry());
        }

        if (!UAStringUtil.isEmpty(locale.getLanguage())) {
            builder.setLanguage(locale.getLanguage());
        }

        builder.setLocationSettings(UAirship.shared().getLocationManager().isLocationUpdatesEnabled());

        if (UAirship.getPackageInfo() != null) {
            builder.setAppVersion(UAirship.getPackageInfo().versionName);
        }

        builder.setSdkVersion(UAirship.getVersion());

        builder.setDeviceModel(Build.MODEL);
        builder.setApiVersion(Build.VERSION.SDK_INT);

        TelephonyManager tm = (TelephonyManager) UAirship.getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
        builder.setCarrier(tm.getNetworkOperatorName());

        for (ChannelRegistrationPayloadExtender extender : channelRegistrationPayloadExtenders) {
            builder = extender.extend(builder);
        }

        return builder.build();
    }

    /**
     * Determines whether tags are enabled on the device.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if tags are enabled on the device, <code>false</code> otherwise.
     */
    public boolean getChannelTagRegistrationEnabled() {
        return channelTagRegistrationEnabled;
    }

    /**
     * Sets whether tags are enabled on the device. The default value is <code>true</code>.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     *
     * @param enabled A boolean indicating whether tags are enabled on the device.
     */
    public void setChannelTagRegistrationEnabled(boolean enabled) {
        channelTagRegistrationEnabled = enabled;
    }

    /**
     * Determines whether channel creation is initially disabled, to be enabled later
     * by enableChannelCreation.
     *
     * @return <code>true</code> if channel creation is initially disabled, <code>false</code> otherwise.
     */
    boolean isChannelCreationDelayEnabled() {
        return channelCreationDelayEnabled;
    }

    /**
     * Check the specified payload and last registration time to determine if registration is required
     *
     * @param payload The channel registration payload
     * @return <code>True</code> if registration is required, <code>false</code> otherwise
     */
    private boolean shouldUpdateRegistration(@NonNull ChannelRegistrationPayload payload) {
        // check time and payload
        ChannelRegistrationPayload lastSuccessPayload = getLastRegistrationPayload();
        if (lastSuccessPayload == null) {
            Logger.verbose("AirshipChannel - Should update registration. Last payload is null.");
            return true;
        }

        long timeSinceLastRegistration = (System.currentTimeMillis() - getLastRegistrationTime());
        if (timeSinceLastRegistration >= CHANNEL_REREGISTRATION_INTERVAL_MS) {
            Logger.verbose("AirshipChannel - Should update registration. Time since last registration time is greater than 24 hours.");
            return true;
        }

        if (!payload.equals(lastSuccessPayload)) {
            Logger.verbose("AirshipChannel - Should update registration. Channel registration payload has changed.");
            return true;
        }

        return false;
    }

    /**
     * Sets the last registration payload and registration time. The last payload and registration
     * time are used to prevent duplicate channel updates.
     *
     * @param channelPayload A ChannelRegistrationPayload.
     */
    private void setLastRegistrationPayload(ChannelRegistrationPayload channelPayload) {
        getDataStore().put(LAST_REGISTRATION_PAYLOAD_KEY, channelPayload);
        getDataStore().put(LAST_REGISTRATION_TIME_KEY, System.currentTimeMillis());
    }

    /**
     * Gets the last registration payload
     *
     * @return a ChannelRegistrationPayload
     */
    @Nullable
    private ChannelRegistrationPayload getLastRegistrationPayload() {
        JsonValue jsonValue = getDataStore().getJsonValue(LAST_REGISTRATION_PAYLOAD_KEY);
        if (jsonValue.isNull()) {
            return null;
        }

        try {
            return ChannelRegistrationPayload.fromJson(jsonValue);
        } catch (JsonException e) {
            Logger.error(e, "AirshipChannel - Failed to parse payload from JSON.");
            return null;
        }
    }

    /**
     * Get the last registration time
     *
     * @return the last registration time
     */
    private long getLastRegistrationTime() {
        long lastRegistrationTime = getDataStore().getLong(LAST_REGISTRATION_TIME_KEY, 0L);

        // If its in the future reset it
        if (lastRegistrationTime > System.currentTimeMillis()) {
            Logger.verbose("Resetting last registration time.");
            getDataStore().put(LAST_REGISTRATION_TIME_KEY, 0);
            return 0;
        }

        return lastRegistrationTime;
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateTagGroup() {
        String channelId = getId();
        if (channelId == null) {
            Logger.verbose("Failed to update channel tags due to null channel ID.");
            return JobInfo.JOB_FINISHED;
        }

        if (tagGroupRegistrar.uploadMutations(TagGroupRegistrar.CHANNEL, channelId)) {
            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_RETRY;
    }

    /**
     * Called to create the channel.
     *
     * @return The job result.
     */
    @WorkerThread
    @JobInfo.JobResult
    private int onCreateChannel() {
        ChannelRegistrationPayload payload = getNextChannelRegistrationPayload();
        ChannelResponse<String> response;
        try {
            response = channelApiClient.createChannelWithPayload(payload);
        } catch (ChannelRequestException e) {
            Logger.debug(e, "Channel registration failed, will retry");
            return JobInfo.JOB_RETRY;
        }

        // 2xx
        if (response.isSuccessful()) {
            String channelId = response.getResult();
            Logger.info("Airship channel created: %s", channelId);
            getDataStore().put(CHANNEL_ID_KEY, channelId);
            setLastRegistrationPayload(payload);
            for (AirshipChannelListener listener : airshipChannelListeners) {
                listener.onChannelCreated(channelId);
            }
            dispatchUpdateTagGroupsJob();
            dispatchUpdateAttributesJob();

            return JobInfo.JOB_FINISHED;
        }

        // 429 || 5xx
        if (response.getStatus() == Response.HTTP_TOO_MANY_REQUESTS || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.debug("Channel registration failed with status: %s, will retry", response.getStatus());
            return JobInfo.JOB_RETRY;
        }

        Logger.debug("Channel registration failed with status: %s", response.getStatus());
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Called to update the channel.
     *
     * @return The job result.
     */
    @WorkerThread
    @JobInfo.JobResult
    private int onUpdateChannel() {
        ChannelRegistrationPayload payload = getNextChannelRegistrationPayload();
        ChannelRegistrationPayload minimizedPayload = payload.minimizedPayload(getLastRegistrationPayload());

        ChannelResponse<Void> response;
        try {
            response = channelApiClient.updateChannelWithPayload(getId(), minimizedPayload);
        } catch (ChannelRequestException e) {
            Logger.debug(e, "Channel registration failed, will retry");
            return JobInfo.JOB_RETRY;
        }

        // 2xx
        if (response.isSuccessful()) {
            Logger.info("Airship channel updated.");
            // Set non-minimized payload as the last sent version, for future comparison
            setLastRegistrationPayload(payload);
            for (AirshipChannelListener listener : airshipChannelListeners) {
                listener.onChannelUpdated(getId());
            }
            return JobInfo.JOB_FINISHED;
        }

        // 429 || 5xx
        if (response.getStatus() == Response.HTTP_TOO_MANY_REQUESTS || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.debug("Channel registration failed with status: %s, will retry", response.getStatus());
            return JobInfo.JOB_RETRY;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
            setLastRegistrationPayload(null);
            getDataStore().remove(CHANNEL_ID_KEY);
            dispatchUpdateRegistrationJob();
            return JobInfo.JOB_FINISHED;
        }

        Logger.debug("Channel registration failed with status: %s", response.getStatus());
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Called to upload attribute mutations.
     *
     * @return The job result.
     */
    @WorkerThread
    @JobInfo.JobResult
    private int onUpdateAttributes() {
        String channelId = getId();
        if (channelId == null) {
            Logger.verbose("Failed to update channel tags due to null channel ID.");
            return JobInfo.JOB_FINISHED;
        }

        if (uploadAttributeMutations(channelId)) {
            return JobInfo.JOB_FINISHED;
        }

        return JobInfo.JOB_RETRY;
    }

    /**
     * Uploads attribute mutations.
     *
     * @param channelId The channel ID.
     *
     * @return {@code true} if uploads are completed, otherwise {@code false}.
     */
    private boolean uploadAttributeMutations(@NonNull String channelId) {
        PendingAttributeMutationStore mutationStore = attributeMutationStore;

        while (true) {
            // Collapse mutations before we try to send any updates
            mutationStore.collapseAndSaveMutations();

            List<PendingAttributeMutation> mutations = mutationStore.peek();
            if (mutations == null) {
                break;
            }

            Response response = attributeApiClient.updateAttributes(channelId, mutations);

            // No response, 5xx, or 429
            if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus()) ||
                    response.getStatus() == Response.HTTP_TOO_MANY_REQUESTS) {
                Logger.debug("Failed to update attributes, retrying.");
                return false;
            }

            // Log 4XX responses (excluding 429)
            if (UAHttpStatusUtil.inClientErrorRange(response.getStatus())) {
                Logger.error("Failed to update attributes with unrecoverable status %s.", response.getStatus());
            }

            mutationStore.pop();

            int status = response.getStatus();
            Logger.debug("Update attributes finished with status: %s", status);
        }

        return true;
    }

    /**
     * Dispatches a job to update registration.
     */
    private void dispatchUpdateRegistrationJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_CHANNEL_REGISTRATION)
                                 .setId(JobInfo.CHANNEL_UPDATE_REGISTRATION)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(AirshipChannel.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * Dispatches a job to update the tag groups.
     */
    private void dispatchUpdateTagGroupsJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_TAG_GROUPS)
                                 .setId(JobInfo.CHANNEL_UPDATE_TAG_GROUPS)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(AirshipChannel.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * Dispatches a job to update the tag groups.
     */
    private void dispatchUpdateAttributesJob() {
        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(ACTION_UPDATE_ATTRIBUTES)
                                 .setId(JobInfo.CHANNEL_UPDATE_ATTRIBUTES)
                                 .setNetworkAccessRequired(true)
                                 .setAirshipComponent(AirshipChannel.class)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }
}
