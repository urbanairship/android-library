/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.amazon.device.messaging.ADMConstants;
import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.amazon.ADMUtils;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.json.JsonException;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * Service delegate for the {@link PushService} to handle channel and push registrations.
 */
class ChannelServiceDelegate extends BaseIntentService.Delegate {

    /**
     * Data store key for the last successfully registered channel payload.
     */
    private static final String LAST_REGISTRATION_PAYLOAD_KEY = "com.urbanairship.push.LAST_REGISTRATION_PAYLOAD";

    /**
     * Data store key for the time in milliseconds of last successfully channel registration.
     */
    private static final String LAST_REGISTRATION_TIME_KEY = "com.urbanairship.push.LAST_REGISTRATION_TIME";

    /**
     * Max time between channel registration updates.
     */
    private static final long CHANNEL_REREGISTRATION_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    private static boolean isPushRegistering = false;
    private static boolean isRegistrationStarted = false;
    private final UAirship airship;
    private final PushManager pushManager;
    private final PushPreferences pushPreferences;
    private ChannelAPIClient channelClient;

    public ChannelServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, new ChannelAPIClient(), UAirship.shared());
    }

    public ChannelServiceDelegate(Context context, PreferenceDataStore dataStore,
                                  ChannelAPIClient channelClient, UAirship airship) {
        super(context, dataStore);

        this.channelClient = channelClient;
        this.airship = airship;
        this.pushManager = airship.getPushManager();
        this.pushPreferences = airship.getPushManager().getPreferences();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case PushService.ACTION_START_REGISTRATION:
                onStartRegistration();
                break;

            case PushService.ACTION_UPDATE_PUSH_REGISTRATION:
                onUpdatePushRegistration(intent);
                break;

            case PushService.ACTION_ADM_REGISTRATION_FINISHED:
                onAdmRegistrationFinished(intent);
                break;

            case PushService.ACTION_UPDATE_CHANNEL_REGISTRATION:
                onUpdateChannelRegistration(intent);
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

        if (isPushRegistrationAllowed() && needsPushRegistration()) {
            isPushRegistering = true;

            // Update the push registration
            Intent updatePushRegistrationIntent = new Intent(getContext(), PushService.class)
                    .setAction(PushService.ACTION_UPDATE_PUSH_REGISTRATION);
            getContext().startService(updatePushRegistrationIntent);
        } else {
            // Update the channel registration
            Intent channelUpdateIntent = new Intent(getContext(), PushService.class)
                    .setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
            getContext().startService(channelUpdateIntent);
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

                if (intent.getBooleanExtra(PushService.EXTRA_GCM_TOKEN_REFRESH, false)) {
                    pushManager.setGcmToken(null);
                    intent.removeExtra(PushService.EXTRA_GCM_TOKEN_REFRESH);
                }

                if (!PlayServicesUtils.isGoogleCloudMessagingDependencyAvailable()) {
                    Logger.error("GCM is unavailable. Unable to register for push notifications. If using " +
                            "the modular Google Play Services dependencies, make sure the application includes " +
                            "the com.google.android.gms:play-services-gcm dependency.");
                    break;
                }

                try {
                    if (!GCMRegistrar.register()) {
                        Logger.error("GCM registration failed.");
                    }
                } catch (IOException e) {
                    Logger.error("GCM registration failed, will retry. GCM error: " + e.getMessage());
                    isPushRegistering = true;
                    retryIntent(intent);
                }

                break;

            case UAirship.AMAZON_PLATFORM:
                if (ADMUtils.isADMSupported()) {
                    ADMUtils.startRegistration(getContext());
                    isPushRegistering = true;
                } else {
                    Logger.error("ADM is not supported on this device.");
                }
                break;
            default:
                Logger.error("Unknown platform type. Unable to register for push.");
        }

        if (!isPushRegistering) {
            // Update the channel registration
            Intent channelUpdateIntent = new Intent(getContext(), PushService.class)
                    .setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);

            getContext().startService(channelUpdateIntent);
        }
    }

    /**
     * Called when ADM registration is finished.
     *
     * @param intent The received intent.
     */
    private void onAdmRegistrationFinished(@NonNull Intent intent) {
        if (airship.getPlatformType() != UAirship.AMAZON_PLATFORM || !ADMUtils.isADMAvailable()) {
            Logger.error("Received intent from invalid transport acting as ADM.");
            return;
        }

        Intent admIntent = intent.getParcelableExtra(PushService.EXTRA_INTENT);
        if (admIntent == null) {
            Logger.error("ChannelServiceDelegate - Received ADM message missing original intent.");
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
        Intent channelUpdateIntent = new Intent(getContext(), PushService.class)
                .setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
        getContext().startService(channelUpdateIntent);
    }

    /**
     * Updates channel registration.
     */
    private void onUpdateChannelRegistration(@NonNull Intent intent) {
        if (isPushRegistering) {
            Logger.verbose("ChannelServiceDelegate - Push registration in progress, skipping registration update.");
            return;
        }

        Logger.verbose("ChannelServiceDelegate - Performing channel registration.");

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        String channelId = pushManager.getChannelId();
        URL channelLocation = getChannelLocationURL();

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
            Logger.verbose("ChannelServiceDelegate - Channel already up to date.");
            return;
        }

        ChannelResponse response = channelClient.updateChannelWithPayload(channelLocation, payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            retryIntent(intent);
            return;
        }

        // 2xx (API should only return 200 or 201)
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Channel registration succeeded with status: " + response.getStatus());

            // Set the last registration payload and time then notify registration succeeded
            setLastRegistrationPayload(payload);
            sendRegistrationFinishedBroadcast(true);
            return;
        }

        // 409
        if (response.getStatus() == HttpURLConnection.HTTP_CONFLICT) {
            // Delete channel and register again.
            pushManager.setChannel(null, null);

            // Update registration
            Intent channelUpdateIntent = new Intent(getContext(), PushService.class)
                    .setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
            getContext().startService(channelUpdateIntent);

            return;
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false);
    }

    /**
     * Actually creates the channel.
     *
     * @param intent The create channel intent.
     * @param payload The ChannelRegistrationPayload payload.
     */
    private void createChannel(@NonNull Intent intent, @NonNull ChannelRegistrationPayload payload) {
        ChannelResponse response = channelClient.createChannelWithPayload(payload);

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            retryIntent(intent);
            return;
        }

        // 200 or 201
        if (response.getStatus() == HttpURLConnection.HTTP_OK || response.getStatus() == HttpURLConnection.HTTP_CREATED) {
            if (!UAStringUtil.isEmpty(response.getChannelLocation()) && !UAStringUtil.isEmpty(response.getChannelId())) {
                Logger.info("Channel creation succeeded with status: " + response.getStatus() + " channel ID: " + response.getChannelId());

                // Set the last registration payload and time then notify registration succeeded
                pushManager.setChannel(response.getChannelId(), response.getChannelLocation());
                setLastRegistrationPayload(payload);
                sendRegistrationFinishedBroadcast(true);

                if (response.getStatus() == HttpURLConnection.HTTP_OK) {
                    // 200 means channel previously existed and a named user may be associated to it.
                    if (airship.getAirshipConfigOptions().clearNamedUser) {
                        // If clearNamedUser is true on re-install, then disassociate if necessary
                        pushManager.getNamedUser().disassociateNamedUserIfNull();
                    }
                }

                // If setId was called before channel creation, update named user
                pushManager.getNamedUser().startUpdateService();
                pushManager.updateRegistration();
                pushManager.startUpdateTagsService();
            } else {
                Logger.error("Failed to register with channel ID: " + response.getChannelId() +
                        " channel location: " + response.getChannelLocation());
                sendRegistrationFinishedBroadcast(false);
            }

            return;
        }

        // Unexpected status code
        Logger.error("Channel registration failed with status: " + response.getStatus());
        sendRegistrationFinishedBroadcast(false);
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
    private URL getChannelLocationURL() {
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
     * Checks if push registration is needed by checking the following:
     * - ADM/GCM registration Id is missing.
     * - Application version code changed since the last registration.
     * - Device ID changed since the last registration.
     * - For GCM, if the GCM sender IDs changed since the last registration.
     *
     * @return <code>true</code> if push registration is needed, otherwise <code>false</code>.
     */
    private boolean needsPushRegistration() {
        if (UAirship.getPackageInfo().versionCode != pushPreferences.getAppVersionCode()) {
            Logger.verbose("ChannelServiceDelegate - Version code changed to " + UAirship.getPackageInfo().versionCode + ". Push re-registration required.");
            return true;
        } else if (!PushManager.getSecureId(getContext()).equals(pushPreferences.getDeviceId())) {
            Logger.verbose("ChannelServiceDelegate - Device ID changed. Push re-registration required.");
            return true;
        }

        switch (airship.getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                if (UAStringUtil.isEmpty(pushManager.getGcmId())) {
                    return true;
                }

                if (UAStringUtil.isEmpty(pushManager.getGcmToken())) {
                    return true;
                }

                Set<String> senderIds = airship.getAirshipConfigOptions().getGCMSenderIds();
                Set<String> registeredGcmSenderIds = pushPreferences.getRegisteredGcmSenderIds();

                // Unregister if we have different registered sender ids
                if (registeredGcmSenderIds != null && !registeredGcmSenderIds.equals(senderIds)) {
                    Logger.verbose("ChannelServiceDelegate - GCM sender IDs changed. Push re-registration required.");
                    return true;
                }

                Logger.verbose("ChannelServiceDelegate - GCM already registered with ID: " + pushManager.getGcmId());
                return false;

            case UAirship.AMAZON_PLATFORM:
                if (UAStringUtil.isEmpty(pushManager.getAdmId())) {
                    return true;
                }

                Logger.verbose("ChannelServiceDelegate - ADM already registered with ID: " + pushManager.getAdmId());
                return false;
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
        String payloadJSON = getDataStore().getString(LAST_REGISTRATION_PAYLOAD_KEY, null);

        try {
            return ChannelRegistrationPayload.parseJson(payloadJSON);
        } catch (JsonException e) {
            Logger.error("ChannelServiceDelegate - Failed to parse payload from JSON.", e);
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
            getDataStore().put(LAST_REGISTRATION_TIME_KEY, 0);
            return 0;
        }

        return lastRegistrationTime;
    }

    /**
     * Broadcasts an intent to notify the host application of a registration finished, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param isSuccess A boolean indicating whether registration succeeded or not.
     */
    private void sendRegistrationFinishedBroadcast(boolean isSuccess) {
        Intent intent = new Intent(PushManager.ACTION_CHANNEL_UPDATED)
                .putExtra(PushManager.EXTRA_CHANNEL_ID, pushManager.getChannelId())
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (!isSuccess) {
            intent.putExtra(PushManager.EXTRA_ERROR, true);
        }

        getContext().sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }
}
