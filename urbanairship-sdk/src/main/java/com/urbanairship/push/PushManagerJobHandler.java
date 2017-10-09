/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import static com.urbanairship.UAirship.getApplicationContext;

/**
 * Job handler for channel registration
 */
class PushManagerJobHandler {

    /**
     * Action to perform update request for pending tag group changes.
     */
    static final String ACTION_UPDATE_TAG_GROUPS = "ACTION_UPDATE_TAG_GROUPS";

    /**
     * Action to handle a scheduled push notification.
     */
    static final String ACTION_PROCESS_PUSH = "ACTION_PROCESS_PUSH";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_PUSH_REGISTRATION = "ACTION_UPDATE_PUSH_REGISTRATION";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_CHANNEL_REGISTRATION = "ACTION_UPDATE_CHANNEL_REGISTRATION";

    /**
     * Data store key for the last successfully registered channel payload.
     */
    private static final String LAST_REGISTRATION_PAYLOAD_KEY = "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD";

    /**
     * Data store key for the time in milliseconds of last successfully channel registration.
     */
    private static final String LAST_REGISTRATION_TIME_KEY = "com.urbanairship.push.LAST_REGISTRATION_TIME";

    /**
     * Response body key for the channel ID.
     */
    private static final String CHANNEL_ID_KEY = "channel_id";

    /**
     * Response header key for the channel location.
     */
    private static final String CHANNEL_LOCATION_KEY = "Location";

    /**
     * Max time between channel registration updates.
     */
    private static final long CHANNEL_REREGISTRATION_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private final UAirship airship;
    private final PushManager pushManager;
    private final ChannelApiClient channelClient;
    private final NamedUser namedUser;
    private final Context context;
    private final PreferenceDataStore dataStore;


    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    PushManagerJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, new ChannelApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    PushManagerJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore,
                          ChannelApiClient channelClient) {
        this.context = context;
        this.dataStore = dataStore;
        this.channelClient = channelClient;
        this.airship = airship;
        this.pushManager = airship.getPushManager();
        this.namedUser = airship.getNamedUser();
    }

    /**
     * Called to handle jobs from {@link PushManager#onPerformJob(UAirship, JobInfo)}.
     *
     * @param jobInfo The airship jobInfo.
     * @return The job result.
     */
    @JobInfo.JobResult
    protected int performJob(JobInfo jobInfo) {
        switch (jobInfo.getAction()) {

            case ACTION_UPDATE_PUSH_REGISTRATION:
                return onUpdatePushRegistration();

            case ACTION_UPDATE_CHANNEL_REGISTRATION:
                return onUpdateChannelRegistration();

            case ACTION_UPDATE_TAG_GROUPS:
                return onUpdateTagGroup();

            case ACTION_PROCESS_PUSH:
                return onProcessPush(jobInfo);
        }

        return JobInfo.JOB_FINISHED;
    }

    @JobInfo.JobResult
    private int onProcessPush(JobInfo jobInfo) {

        PushMessage message = PushMessage.fromJsonValue(jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PUSH));
        String providerClass = jobInfo.getExtras().opt(PushProviderBridge.EXTRA_PROVIDER_CLASS).getString();

        if (message == null || providerClass == null) {
            return JobInfo.JOB_FINISHED;
        }

        IncomingPushRunnable pushRunnable = new IncomingPushRunnable.Builder(getApplicationContext())
                .setLongRunning(true)
                .setMessage(message)
                .setProviderClass(providerClass)
                .build();

        pushRunnable.run();


        return JobInfo.JOB_FINISHED;
    }


    /**
     * Updates the push registration.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdatePushRegistration() {
        PushProvider provider = pushManager.getPushProvider();
        String currentToken = pushManager.getRegistrationToken();

        if (provider == null) {
            Logger.error("Registration failed. Missing push provider.");
            return JobInfo.JOB_FINISHED;
        }

        if (!provider.isAvailable(context)) {
            Logger.error("Registration failed. Push provider unavailable: " + provider);
            return JobInfo.JOB_RETRY;
        }

        String token;
        try {
            token = provider.getRegistrationToken(context);
        } catch (PushProvider.RegistrationException e) {
            Logger.error("Push registration failed.", e);
            if (e.isRecoverable()) {
                return JobInfo.JOB_RETRY;
            } else {
                return JobInfo.JOB_FINISHED;
            }
        }

        if (!UAStringUtil.equals(token, currentToken)) {
            Logger.info("PushManagerJobHandler - Push registration updated.");
            pushManager.setRegistrationToken(token);
        }

        pushManager.updateRegistration();
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Updates channel registration.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateChannelRegistration() {
        Logger.verbose("PushManagerJobHandler - Performing channel registration.");

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        String channelId = pushManager.getChannelId();
        URL channelLocation = getChannelLocationUrl();

        if (channelLocation != null && !UAStringUtil.isEmpty(channelId)) {
            return updateChannel(channelLocation, payload);
        } else {
            return createChannel(payload);
        }
    }

    /**
     * Updates a channel.
     *
     * @param channelLocation Channel location.
     * @param payload The ChannelRegistrationPayload payload.
     * @return The job result.
     */
    @JobInfo.JobResult
    private int updateChannel(@NonNull URL channelLocation, @NonNull ChannelRegistrationPayload payload) {
        if (!shouldUpdateRegistration(payload)) {
            Logger.verbose("PushManagerJobHandler - Channel already up to date.");
            return JobInfo.JOB_FINISHED;
        }

        Response response = channelClient.updateChannelWithPayload(channelLocation, payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            sendRegistrationFinishedBroadcast(false, false);
            return JobInfo.JOB_RETRY;
        }

        // 2xx (API should only return 200 or 201)
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Channel registration succeeded with status: " + response.getStatus());

            // Set the last registration payload and time then notify registration succeeded
            setLastRegistrationPayload(payload);
            sendRegistrationFinishedBroadcast(true, false);

            if (shouldUpdateRegistration(payload)) {
                pushManager.updateRegistration();
            }

            return JobInfo.JOB_FINISHED;
        }

        // 409
        if (response.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
            // Delete channel and register again.
            pushManager.setChannel(null, null);
            return createChannel(payload);
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false, false);
        return JobInfo.JOB_FINISHED;
    }

    /**
     * Actually creates the channel.
     *
     * @param payload The ChannelRegistrationPayload payload.
     * @return The job result.
     */
    @JobInfo.JobResult
    private int createChannel(@NonNull ChannelRegistrationPayload payload) {

        if (pushManager.isChannelCreationDelayEnabled()) {
            Logger.info("Channel registration is currently disabled.");
            return JobInfo.JOB_FINISHED;
        }

        Response response = channelClient.createChannelWithPayload(payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            sendRegistrationFinishedBroadcast(false, true);
            return JobInfo.JOB_RETRY;
        }

        // 200 or 201
        if (response.getStatus() == HttpURLConnection.HTTP_OK || response.getStatus() == HttpURLConnection.HTTP_CREATED) {
            String channelId = null;
            try {
                channelId = JsonValue.parseString(response.getResponseBody()).optMap().opt(CHANNEL_ID_KEY).getString();
            } catch (JsonException e) {
                Logger.debug("Unable to parse channel registration response body: " + response.getResponseBody(), e);
            }

            String channelLocation = response.getResponseHeader(CHANNEL_LOCATION_KEY);

            if (!UAStringUtil.isEmpty(channelLocation) && !UAStringUtil.isEmpty(channelId)) {
                Logger.info("Channel creation succeeded with status: " + response.getStatus() + " channel ID: " + channelId);

                // Set the last registration payload and time then notify registration succeeded
                pushManager.setChannel(channelId, channelLocation);
                setLastRegistrationPayload(payload);
                sendRegistrationFinishedBroadcast(true, true);

                if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                    // 200 means channel previously existed and a named user may be associated to it.
                    if (airship.getAirshipConfigOptions().clearNamedUser) {
                        // If clearNamedUser is true on re-install, then disassociate if necessary
                        namedUser.disassociateNamedUserIfNull();
                    }
                }

                // If setId was called before channel creation, update named user
                namedUser.dispatchNamedUserUpdateJob();

                if (shouldUpdateRegistration(payload)) {
                    pushManager.updateRegistration();
                }

                pushManager.dispatchUpdateTagGroupsJob();
                airship.getInbox().getUser().update(true);

                // Send analytics event
                airship.getAnalytics().uploadEvents();

            } else {
                Logger.error("Failed to register with channel ID: " + channelId +
                        " channel location: " + channelLocation);
                sendRegistrationFinishedBroadcast(false, true);
                return JobInfo.JOB_RETRY;
            }

            return JobInfo.JOB_FINISHED;
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false, true);

        return JobInfo.JOB_FINISHED;
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
            Logger.verbose("PushManagerJobHandler - Should update registration. Last payload is null.");
            return true;
        }


        long timeSinceLastRegistration = (System.currentTimeMillis() - getLastRegistrationTime());
        if (timeSinceLastRegistration >= CHANNEL_REREGISTRATION_INTERVAL_MS) {
            Logger.verbose("PushManagerJobHandler - Should update registration. Time since last registration time is greater than 24 hours.");
            return true;
        }

        if (!payload.equals(lastSuccessPayload)) {
            Logger.verbose("PushManagerJobHandler - Should update registration. Channel registration payload has changed.");
            return true;
        }

        return false;
    }

    /**
     * Get the channel location as a URL
     *
     * @return The channel location URL
     */
    @Nullable
    private URL getChannelLocationUrl() {
        String channelLocationString = pushManager.getChannelLocation();
        if (!UAStringUtil.isEmpty(channelLocationString)) {
            try {
                return new URL(channelLocationString);
            } catch (MalformedURLException e) {
                Logger.error("Channel location from preferences was invalid: " + channelLocationString, e);
            }
        }

        return null;
    }

    /**
     * Sets the last registration payload and registration time. The last payload and registration
     * time are used to prevent duplicate channel updates.
     *
     * @param channelPayload A ChannelRegistrationPayload.
     */
    private void setLastRegistrationPayload(ChannelRegistrationPayload channelPayload) {
        dataStore.put(LAST_REGISTRATION_PAYLOAD_KEY, channelPayload);
        dataStore.put(LAST_REGISTRATION_TIME_KEY, System.currentTimeMillis());
    }

    /**
     * Gets the last registration payload
     *
     * @return a ChannelRegistrationPayload
     */
    @Nullable
    private ChannelRegistrationPayload getLastRegistrationPayload() {
        try {
            return ChannelRegistrationPayload.parseJson(dataStore.getJsonValue(LAST_REGISTRATION_PAYLOAD_KEY));
        } catch (JsonException e) {
            Logger.error("PushManagerJobHandler - Failed to parse payload from JSON.", e);
            return null;
        }
    }

    /**
     * Get the last registration time
     *
     * @return the last registration time
     */
    private long getLastRegistrationTime() {
        long lastRegistrationTime = dataStore.getLong(LAST_REGISTRATION_TIME_KEY, 0L);

        // If its in the future reset it
        if (lastRegistrationTime > System.currentTimeMillis()) {
            Logger.verbose("Resetting last registration time.");
            dataStore.put(LAST_REGISTRATION_TIME_KEY, 0);
            return 0;
        }

        return lastRegistrationTime;
    }

    /**
     * Broadcasts an intent to notify the host application of a registration finished, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param isSuccess A boolean indicating whether registration succeeded or not.
     * @param isCreateRequest A boolean indicating the channel registration request type - true if
     * the request is of the create type, false otherwise.
     */
    private void sendRegistrationFinishedBroadcast(boolean isSuccess, boolean isCreateRequest) {
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_CHANNEL_ID, pushManager.getChannelId())
                .putExtra(PushManager.EXTRA_CHANNEL_CREATE_REQUEST, isCreateRequest)
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (!isSuccess) {
            intent.putExtra(PushManager.EXTRA_ERROR, true);
        }

        context.sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }


    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @JobInfo.JobResult
    private int onUpdateTagGroup() {
        String channelId = pushManager.getChannelId();
        if (channelId == null) {
            Logger.verbose("Failed to update channel tags due to null channel ID.");
            return JobInfo.JOB_FINISHED;
        }

        TagGroupsMutation mutation;
        while ((mutation = pushManager.getTagGroupStore().pop()) != null) {
            Response response = channelClient.updateTagGroups(channelId, mutation);

            // 5xx or no response
            if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
                Logger.info("PushManagerJobHandler - Failed to update tag groups, will retry later.");
                pushManager.getTagGroupStore().push(mutation);
                return JobInfo.JOB_RETRY;
            }

            int status = response.getStatus();
            Logger.info("PushManagerJobHandler - Update tag groups finished with status: " + status);
        }

        return JobInfo.JOB_FINISHED;
    }
}
