/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.amazon.AdmUtils;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

/**
 * Intent handler for channel registration
 */
class ChannelIntentHandler {

    /**
     * Action to update pending channel tag groups.
     */
    static final String ACTION_APPLY_TAG_GROUP_CHANGES = "com.urbanairship.push.ACTION_APPLY_TAG_GROUP_CHANGES";

    /**
     * Action to perform update request for pending tag group changes.
     */
    static final String ACTION_UPDATE_TAG_GROUPS = "com.urbanairship.push.ACTION_UPDATE_TAG_GROUPS";

    /**
     * Action to start channel and push registration.
     */
    static final String ACTION_START_REGISTRATION = "com.urbanairship.push.ACTION_START_REGISTRATION";

    /**
     * Action notifying the service that ADM registration has finished.
     */
    static final String ACTION_ADM_REGISTRATION_FINISHED = "com.urbanairship.push.ACTION_ADM_REGISTRATION_FINISHED";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_PUSH_REGISTRATION = "com.urbanairship.push.ACTION_UPDATE_PUSH_REGISTRATION";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_CHANNEL_REGISTRATION = "com.urbanairship.push.ACTION_UPDATE_CHANNEL_REGISTRATION";

    /**
     * Extra containing the received message intent.
     */
    static final String EXTRA_INTENT = "com.urbanairship.push.EXTRA_INTENT";

    /**
     * Data store key for the last successfully registered channel payload.
     */
    private static final String LAST_REGISTRATION_PAYLOAD_KEY = "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD";

    /**
     * Data store key for the time in milliseconds of last successfully channel registration.
     */
    private static final String LAST_REGISTRATION_TIME_KEY = "com.urbanairship.push.LAST_REGISTRATION_TIME";

    /**
     * Key for storing the pending channel add tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_CHANNEL_ADD_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_ADD_TAG_GROUPS";

    /**
     * Key for storing the pending channel remove tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_REMOVE_TAG_GROUPS";

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

    private static boolean isPushRegistering = false;
    private static boolean isRegistrationStarted = false;
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
    ChannelIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, new ChannelApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    ChannelIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore,
                                ChannelApiClient channelClient) {
        this.context = context;
        this.dataStore = dataStore;
        this.channelClient = channelClient;
        this.airship = airship;
        this.pushManager = airship.getPushManager();
        this.namedUser = airship.getNamedUser();
    }

    /**
     * Handles {@link AirshipService} intents for {@link com.urbanairship.push.PushManager}.
     *
     * @param intent The intent.
     */
    protected void handleIntent(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_START_REGISTRATION:
                onStartRegistration();
                break;

            case ACTION_UPDATE_PUSH_REGISTRATION:
                onUpdatePushRegistration(intent);
                break;

            case ACTION_ADM_REGISTRATION_FINISHED:
                onAdmRegistrationFinished(intent);
                break;

            case ACTION_UPDATE_CHANNEL_REGISTRATION:
                onUpdateChannelRegistration(intent);
                break;

            case ACTION_APPLY_TAG_GROUP_CHANGES:
                onApplyTagGroupChanges(intent);
                break;

            case ACTION_UPDATE_TAG_GROUPS:
                onUpdateTagGroup(intent);
                break;
        }
    }

    /**
     * Starts the registration process. Will either start the push registration flow or channel registration
     * depending on if push registration is needed.
     */
    private void onStartRegistration() {
        if (isRegistrationStarted) {
            // Happens anytime we have multiple processes
            return;
        }

        isRegistrationStarted = true;

        if (isPushRegistrationAllowed()) {
            isPushRegistering = true;

            // Update the push registration
            Intent updatePushRegistrationIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_PUSH_REGISTRATION);
            context.startService(updatePushRegistrationIntent);
        } else {
            // Update the channel registration
            Intent channelUpdateIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_CHANNEL_REGISTRATION);
            context.startService(channelUpdateIntent);
        }
    }

    /**
     * Updates the push registration for either ADM or GCM.
     *
     * @param intent The push registration update intent.
     */
    private void onUpdatePushRegistration(@NonNull Intent intent) {
        isPushRegistering = false;

        switch (airship.getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:

                if (!PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
                    Logger.error("GCM is unavailable. Unable to register for push notifications. If using " +
                            "the modular Google Play Services dependencies, make sure the application includes " +
                            "the com.google.android.gms:play-services-gcm dependency.");
                    break;
                }

                try {
                    GcmRegistrar.register();
                } catch (IOException | SecurityException e) {
                    Logger.error("GCM registration failed, will retry. GCM error: " + e.getMessage());
                    isPushRegistering = true;
                    AirshipService.retryServiceIntent(context, intent);
                }

                break;

            case UAirship.AMAZON_PLATFORM:

                if (!AdmUtils.isAdmSupported()) {
                    Logger.error("ADM is not supported on this device.");
                    break;
                }

                String admId = AdmUtils.getRegistrationId(context);
                if (admId == null) {
                    pushManager.setAdmId(null);
                    AdmUtils.startRegistration(context);
                    isPushRegistering = true;
                } else if (!admId.equals(pushManager.getAdmId())) {
                    Logger.info("ADM registration successful. Registration ID: " + admId);
                    pushManager.setAdmId(admId);
                }

                break;
            default:
                Logger.error("Unknown platform type. Unable to register for push.");
        }

        if (!isPushRegistering) {
            // Update the channel registration
            Intent channelUpdateIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_CHANNEL_REGISTRATION);

            context.startService(channelUpdateIntent);
        }
    }

    /**
     * Called when ADM registration is finished.
     *
     * @param intent The received intent.
     */
    private void onAdmRegistrationFinished(@NonNull Intent intent) {
        if (airship.getPlatformType() != UAirship.AMAZON_PLATFORM || !AdmUtils.isAdmAvailable()) {
            Logger.error("Received intent from invalid transport acting as ADM.");
            return;
        }

        Intent admIntent = intent.getParcelableExtra(EXTRA_INTENT);
        if (admIntent == null) {
            Logger.error("ChannelIntentHandler - Received ADM message missing original intent.");
            return;
        }

        if (admIntent.hasExtra(ADMConstants.LowLevel.EXTRA_ERROR)) {
            Logger.error("ADM error occurred: " + admIntent.getStringExtra(ADMConstants.LowLevel.EXTRA_ERROR));
        } else {
            String registrationID = admIntent.getStringExtra(ADMConstants.LowLevel.EXTRA_REGISTRATION_ID);
            if (registrationID != null) {
                Logger.info("ADM registration successful. Registration ID: " + registrationID);
                pushManager.setAdmId(registrationID);
            }
        }

        isPushRegistering = false;

        // Update the channel registration
        Intent channelUpdateIntent = new Intent(context, AirshipService.class)
                .setAction(ACTION_UPDATE_CHANNEL_REGISTRATION);
        context.startService(channelUpdateIntent);
    }

    /**
     * Updates channel registration.
     */
    private void onUpdateChannelRegistration(@NonNull Intent intent) {
        if (isPushRegistering) {
            Logger.verbose("ChannelIntentHandler - Push registration in progress, skipping registration update.");
            return;
        }

        Logger.verbose("ChannelIntentHandler - Performing channel registration.");

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        String channelId = pushManager.getChannelId();
        URL channelLocation = getChannelLocationUrl();

        if (channelLocation != null && !UAStringUtil.isEmpty(channelId)) {
            updateChannel(intent, channelLocation, payload);
        } else {
            createChannel(intent, payload);
        }
    }

    /**
     * Updates a channel.
     *
     * @param intent The update channel intent.
     * @param channelLocation Channel location.
     * @param payload The ChannelRegistrationPayload payload.
     */
    private void updateChannel(@NonNull Intent intent, @NonNull URL channelLocation, @NonNull ChannelRegistrationPayload payload) {
        if (!shouldUpdateRegistration(payload)) {
            Logger.verbose("ChannelIntentHandler - Channel already up to date.");
            return;
        }

        Response response = channelClient.updateChannelWithPayload(channelLocation, payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            AirshipService.retryServiceIntent(context, intent);
            sendRegistrationFinishedBroadcast(false, false);
            return;
        }

        // 2xx (API should only return 200 or 201)
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Channel registration succeeded with status: " + response.getStatus());

            // Set the last registration payload and time then notify registration succeeded
            setLastRegistrationPayload(payload);
            sendRegistrationFinishedBroadcast(true, false);
            return;
        }

        // 409
        if (response.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
            // Delete channel and register again.
            pushManager.setChannel(null, null);

            // Update registration
            Intent channelUpdateIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_CHANNEL_REGISTRATION);
            context.startService(channelUpdateIntent);

            return;
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false, false);
    }

    /**
     * Actually creates the channel.
     *
     * @param intent The create channel intent.
     * @param payload The ChannelRegistrationPayload payload.
     */
    private void createChannel(@NonNull Intent intent, @NonNull ChannelRegistrationPayload payload) {

        if (pushManager.isChannelCreationDelayEnabled()) {
            Logger.info("Channel registration is currently disabled.");
            return;
        }

        Response response = channelClient.createChannelWithPayload(payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            sendRegistrationFinishedBroadcast(false, true);
            AirshipService.retryServiceIntent(context, intent);
            return;
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
                namedUser.startUpdateService();
                pushManager.updateRegistration();
                pushManager.startUpdateTagsService();
                airship.getInbox().getUser().update(true);

                // Send analytics event
                airship.getAnalytics().uploadEvents();

            } else {
                Logger.error("Failed to register with channel ID: " + channelId +
                        " channel location: " + channelLocation);
                sendRegistrationFinishedBroadcast(false, true);
            }

            return;
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false, true);
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
        long timeSinceLastRegistration = (System.currentTimeMillis() - getLastRegistrationTime());
        return (!payload.equals(lastSuccessPayload)) ||
                (timeSinceLastRegistration >= CHANNEL_REREGISTRATION_INTERVAL_MS);
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
     * Check if the push registration is allowed for the current platform.
     *
     * @return <code>true</code> if push registration is allowed.
     */
    private boolean isPushRegistrationAllowed() {
        switch (airship.getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                if (!airship.getAirshipConfigOptions().isTransportAllowed(AirshipConfigOptions.GCM_TRANSPORT)) {
                    Logger.info("Unable to register for push. GCM transport type is not allowed.");
                    return false;
                }
                return true;
            case UAirship.AMAZON_PLATFORM:
                if (!airship.getAirshipConfigOptions().isTransportAllowed(AirshipConfigOptions.ADM_TRANSPORT)) {
                    Logger.info("Unable to register for push. ADM transport type is not allowed.");
                    return false;
                }
                return true;
            default:
                return false;
        }
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
        String payloadJSON = dataStore.getString(LAST_REGISTRATION_PAYLOAD_KEY, null);

        try {
            return ChannelRegistrationPayload.parseJson(payloadJSON);
        } catch (JsonException e) {
            Logger.error("ChannelIntentHandler - Failed to parse payload from JSON.", e);
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
     * @param intent The update intent
     */
    private void onUpdateTagGroup(Intent intent) {
        String channelId = pushManager.getChannelId();
        if (channelId == null) {
            Logger.verbose("Failed to update channel tags due to null channel ID.");
            return;
        }

        Map<String, Set<String>> pendingAddTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_CHANNEL_ADD_TAG_GROUPS_KEY));
        Map<String, Set<String>> pendingRemoveTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY));

        // Make sure we actually have tag changes to perform
        if (pendingAddTags.isEmpty() && pendingRemoveTags.isEmpty()) {
            Logger.verbose("Channel pending tag group changes empty. Skipping update.");
            return;
        }

        Response response = channelClient.updateTagGroups(channelId, pendingAddTags, pendingRemoveTags);

        // 5xx or no response
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.info("Failed to update tag groups, will retry. Saved pending tag groups.");

            // Retry later
            AirshipService.retryServiceIntent(context, intent);

            return;
        }

        int status = response.getStatus();

        Logger.info("Channel tag groups update finished with status: " + status);

        // Clear pending groups if success, forbidden, or bad request
        if (UAHttpStatusUtil.inSuccessRange(status) || status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_BAD_REQUEST) {
            // Clear pending
            dataStore.remove(PENDING_CHANNEL_ADD_TAG_GROUPS_KEY);
            dataStore.remove(PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY);
        }
    }

    /**
     * Handles any pending tag group changes.
     *
     * @param intent The tag group intent.
     */
    private void onApplyTagGroupChanges(Intent intent) {
        Map<String, Set<String>> pendingAddTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_CHANNEL_ADD_TAG_GROUPS_KEY));
        Map<String, Set<String>> pendingRemoveTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY));

        // Add tags from bundle to pendingAddTags and remove them from pendingRemoveTags.
        Bundle addTagsBundle = intent.getBundleExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS);
        TagUtils.combineTagGroups(addTagsBundle, pendingAddTags, pendingRemoveTags);

        // Add tags from bundle to pendingRemoveTags and remove them from pendingAddTags.
        Bundle removeTagsBundle = intent.getBundleExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS);
        TagUtils.combineTagGroups(removeTagsBundle, pendingRemoveTags, pendingAddTags);

        dataStore.put(PENDING_CHANNEL_ADD_TAG_GROUPS_KEY, JsonValue.wrapOpt(pendingAddTags));
        dataStore.put(PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY, JsonValue.wrapOpt(pendingRemoveTags));

        // Make sure we actually have tag changes to perform
        if (pushManager.getChannelId() != null && (!pendingAddTags.isEmpty() || !pendingRemoveTags.isEmpty())) {
            Intent updateIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_TAG_GROUPS);

            context.startService(updateIntent);
        }
    }

}
