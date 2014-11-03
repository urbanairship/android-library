/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.util.SparseArray;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.google.PlayServicesUtils;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

/**
 * Service class for handling push notifications.
 */
public class PushService extends IntentService {

    /**
     * Max time between channel registration updates.
     */
    private static final long CHANNEL_REREGISTRATION_INTERVAL_MS = 24 * 60 * 60 * 1000; //24H

    /**
     * The starting back off time for channel and push registration retries.
     */
    private static final long STARTING_BACK_OFF_TIME = 10000; // 10 seconds.

    /**
     * The max back off time for channel and push registration retries.
     */
    private static final long MAX_BACK_OFF_TIME = 5120000; // About 85 mins.

    /**
     * The timeout before a wake lock is released.
     */
    private static final long WAKE_LOCK_TIMEOUT_MS = 60 * 1000; // 1 minute

    /**
     * Action to start channel and push registration.
     */
    static final String ACTION_START_REGISTRATION = "com.urbanairship.push.ACTION_START_REGISTRATION";

    /**
     * Action notifying the service that push registration has finished.
     */
    static final String ACTION_PUSH_REGISTRATION_FINISHED = "com.urbanairship.push.ACTION_PUSH_REGISTRATION_FINISHED";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_REGISTRATION = "com.urbanairship.push.ACTION_UPDATE_REGISTRATION";

    /**
     * Action to retry channel registration.
     */
    static final String ACTION_RETRY_CHANNEL_REGISTRATION = "com.urbanairship.push.ACTION_RETRY_CHANNEL_REGISTRATION";

    /**
     * Action to retry push registration.
     */
    static final String ACTION_RETRY_PUSH_REGISTRATION = "com.urbanairship.push.ACTION_RETRY_PUSH_REGISTRATION";

    /**
     * Action sent when a push is received.
     */
    static final String ACTION_PUSH_RECEIVED = "com.urbanairship.push.ACTION_PUSH_RECEIVED";

    /**
     * Extra for wake lock ID. Set and removed by the service.
     */
    static final String EXTRA_WAKE_LOCK_ID = "com.urbanairship.push.EXTRA_WAKE_LOCK_ID";

    /**
     * Extra that stores the back off time on the retry intents.
     */
    static final String EXTRA_BACK_OFF = "com.urbanairship.push.EXTRA_BACK_OFF";

    private static final SparseArray<WakeLock> wakeLocks = new SparseArray<WakeLock>();
    private static int nextWakeLockID = 0;
    private static boolean isPushRegistering = false;

    private static long channelRegistrationBackOff = 0;

    private static long pushRegistrationBackOff = 0;

    private ChannelAPIClient channelClient;

    /**
     * PushService constructor.
     */
    public PushService() {
        super("PushService");
    }

    /**
     * PushService constructor that specifies the channel client. Used
     * for testing.
     * @param client The channel api client.
     */
    PushService(ChannelAPIClient client) {
        super("PushService");
        this.channelClient = client;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Logger.verbose("Push service started with intent: " + intent);

        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        int wakeLockId = intent.getIntExtra(EXTRA_WAKE_LOCK_ID, -1);
        intent.removeExtra(EXTRA_WAKE_LOCK_ID);

        try {
            if (ACTION_PUSH_RECEIVED.equals(action)) {
                onPushReceived(intent);
            } else if (ACTION_PUSH_REGISTRATION_FINISHED.equals(action)) {
                onPushRegistrationFinished();
            } else if (ACTION_UPDATE_REGISTRATION.equals(action)) {
                onUpdateRegistration();
            } else if (ACTION_START_REGISTRATION.equals(action)) {
                onStartRegistration();
            } else if (ACTION_RETRY_CHANNEL_REGISTRATION.equals(action)) {
                onRetryChannelRegistration(intent);
            } else if (ACTION_RETRY_PUSH_REGISTRATION.equals(action)) {
                onRetryPushRegistration(intent);
            }
        } finally {
            if (wakeLockId >= 0) {
                releaseWakeLock(wakeLockId);
            }
        }
    }

    /**
     * The PushMessage will be parsed from the intent and delivered to
     * the PushManager.
     *
     * @param intent The value passed to onHandleIntent.
     */
    private void onPushReceived(Intent intent) {
        PushMessage message = new PushMessage(intent.getExtras());
        Logger.info("Received push message: " + message);
        UAirship.shared().getPushManager().deliverPush(message);
    }

    /**
     * Starts push registration or it will update channel registration. When
     * push registration is started, an intent with ACTION_PUSH_REGISTRATION_FINISHED
     * will be received which will trigger a channel update. While push registration
     * is in progress, any update registrations will be ignored.
     */
    private void onStartRegistration() {
        if (isPushRegistering) {
            // This will occur anytime we have multiple processes.
            return;
        }

        if (isPushRegistrationAllowed() && needsPushRegistration()) {
            startPushRegistration();
        } else {
            performChannelRegistration();
        }
    }

    /**
     * Updates channel registration.
     */
    private void onUpdateRegistration() {
        if (isPushRegistering) {
            Logger.verbose("Push registration in progress, skipping registration update.");
            return;
        }

        performChannelRegistration();
    }

    /**
     * Called when push registration is finished. Will trigger a channel registration
     * update.
     */
    private void onPushRegistrationFinished() {
        isPushRegistering = false;
        performChannelRegistration();
    }

    /**
     * Called when a push registration previously failed and is being retried.
     *
     * @param intent The value passed to onHandleIntent.
     */
    private void onRetryPushRegistration(Intent intent) {
        // Restore the back off if the application was restarted since the last retry.
        pushRegistrationBackOff = intent.getLongExtra(EXTRA_BACK_OFF, pushRegistrationBackOff);
        if (isPushRegistrationAllowed() && needsPushRegistration()) {
            startPushRegistration();
        }
    }

    /**
     * Called when a channel registration previously failed and is being retried.
     *
     * @param intent The value passed to onHandleIntent.
     */
    private void onRetryChannelRegistration(Intent intent) {
        // Restore the back off if the application was restarted since the last retry.
        channelRegistrationBackOff = intent.getLongExtra(EXTRA_BACK_OFF, channelRegistrationBackOff);
        performChannelRegistration();
    }

    /**
     * Updates a channel.
     *
     * @param channelLocation Channel location.
     * @param payload The ChannelRegistrationPayload payload.
     */
    private void updateChannel(URL channelLocation, ChannelRegistrationPayload payload) {
        PushManager pushManager = UAirship.shared().getPushManager();
        PushPreferences pushPreferences = pushManager.getPreferences();

        ChannelResponse response = getChannelClient().updateChannelWithPayload(channelLocation, payload);

        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            channelRegistrationBackOff = calculateNextBackOff(channelRegistrationBackOff);
            scheduleRetry(ACTION_RETRY_CHANNEL_REGISTRATION, channelRegistrationBackOff);
        } else if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Channel registration succeeded with status: " + response.getStatus());

            // Set the last registration payload and time then notify registration succeeded
            pushPreferences.setLastRegistrationPayload(payload);
            pushPreferences.setLastRegistrationTime(System.currentTimeMillis());
            pushManager.sendRegistrationFinishedBroadcast(true);

            channelRegistrationBackOff = 0;
        } else if (response.getStatus() == 409) {
            // 409 Conflict. Delete channel and register again.
            pushManager.setChannel(null, null);
            pushPreferences.setLastRegistrationPayload(null);
            performChannelRegistration();
        } else {
            // Got an unexpected status code, so notify registration failed
            Logger.error("Channel registration failed with status: " + response.getStatus());
            pushManager.sendRegistrationFinishedBroadcast(false);

            channelRegistrationBackOff = 0;
        }
    }

    /**
     * Actually creates the channel.
     *
     * @param payload The ChannelRegistrationPayload payload.
     */
    private void createChannel(ChannelRegistrationPayload payload) {
        PushManager pushManager = UAirship.shared().getPushManager();
        PushPreferences pushPreferences = pushManager.getPreferences();
        ChannelResponse response = getChannelClient().createChannelWithPayload(payload);

        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.error("Channel registration failed, will retry.");
            channelRegistrationBackOff = calculateNextBackOff(channelRegistrationBackOff);
            scheduleRetry(ACTION_RETRY_CHANNEL_REGISTRATION, channelRegistrationBackOff);
        } else if (response.getStatus() == HttpURLConnection.HTTP_OK || response.getStatus() == HttpURLConnection.HTTP_CREATED) {
            Logger.info("Channel creation succeeded with status: " + response.getStatus());

            if (!UAStringUtil.isEmpty(response.getChannelLocation()) && !UAStringUtil.isEmpty(response.getChannelId())) {
                // Set the last registration payload and time then notify registration succeeded
                pushManager.setChannel(response.getChannelId(), response.getChannelLocation());
                pushPreferences.setLastRegistrationPayload(payload);
                pushPreferences.setLastRegistrationTime(System.currentTimeMillis());
                pushManager.sendRegistrationFinishedBroadcast(true);
            } else {
                Logger.error("Failed to register with channel ID: " + response.getChannelId() +
                        " channel location: " + response.getChannelLocation());
                pushManager.sendRegistrationFinishedBroadcast(false);
            }

            channelRegistrationBackOff = 0;
        } else {
            // Got an unexpected status code, so notify registration failed
            Logger.error("Channel registration failed with status: " + response.getStatus());
            pushManager.sendRegistrationFinishedBroadcast(false);

            channelRegistrationBackOff = 0;
        }
    }

    /**
     * Performs channel registration. Will either result in updating or creating a channel.
     */
    private void performChannelRegistration() {
        Logger.verbose("Performing channel registration.");
        PushManager pushManager = UAirship.shared().getPushManager();
        PushPreferences pushPreferences = pushManager.getPreferences();

        ChannelRegistrationPayload payload = pushManager.getNextChannelRegistrationPayload();
        if (!shouldUpdateRegistration(payload)) {
            Logger.verbose("Channel already up to date.");
            return;
        }

        String channelId = pushPreferences.getChannelId();
        URL channelLocation = getChannelLocationURL();

        if (channelLocation != null && !UAStringUtil.isEmpty(channelId)) {
            updateChannel(channelLocation, payload);
        } else {
            createChannel(payload);
        }
    }

    /**
     * Scheduled an intent for the service.
     *
     * @param action The action to schedule.
     * @param delay The delay in milliseconds.
     */
    private void scheduleRetry(String action, long delay) {
        Logger.info("Rescheduling push service " + action + " in " + delay + " milliseconds.");

        Intent intent = new Intent(getApplicationContext(), PushService.class)
                .setAction(action)
                .putExtra(EXTRA_BACK_OFF, delay);

        AlarmManager alarmManager = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + delay, pendingIntent);
    }

    /**
     * Calculate the next back off value.
     *
     * @param lastBackOff The last back off value.
     * @return The next back off value.
     */
    private long calculateNextBackOff(long lastBackOff) {
        long delay = Math.min(lastBackOff * 2, MAX_BACK_OFF_TIME);
        return Math.max(delay, STARTING_BACK_OFF_TIME);
    }

    /**
     * Starts registering for push with GCM.
     */
    private void startPushRegistration() {

        isPushRegistering = true;

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                if (!PlayServicesUtils.isGooglePlayServicesDependencyAvailable()) {
                    Logger.info("Unable to start registration. Google Play services unavailable.");
                    performChannelRegistration();
                } else {
                    try {
                        if (!GCMRegistrar.register()) {
                            Logger.error("GCM registration failed.");
                            isPushRegistering = false;
                            pushRegistrationBackOff = 0;
                            performChannelRegistration();
                        }
                    } catch (IOException e) {
                        Logger.error("GCM registration failed, will retry. GCM error: " + e.getMessage());
                        pushRegistrationBackOff = calculateNextBackOff(pushRegistrationBackOff);
                        scheduleRetry(ACTION_RETRY_PUSH_REGISTRATION, pushRegistrationBackOff);
                    }
                }
                break;

            case UAirship.AMAZON_PLATFORM:
                if (!ADMRegistrar.register()) {
                    Logger.error("ADM registration failed.");
                    isPushRegistering = false;
                    pushRegistrationBackOff = 0;
                    performChannelRegistration();
                }
                break;

            default:
                Logger.error("Unknown platform type. Unable to register for push.");
                isPushRegistering = false;
                performChannelRegistration();
        }
    }

    /**
     * Get the channel location as a URL
     *
     * @return The channel location URL
     */
    private URL getChannelLocationURL() {
        String channelLocationString = UAirship.shared().getPushManager().getPreferences().getChannelLocation();
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
     * Check the specified payload and last registration time to determine if registration is required
     *
     * @param payload The channel registration payload
     * @return <code>True</code> if registration is required, <code>false</code> otherwise
     */
    private boolean shouldUpdateRegistration(ChannelRegistrationPayload payload) {
        PushPreferences pushPreferences = UAirship.shared().getPushManager().getPreferences();

        // check time and payload
        ChannelRegistrationPayload lastSuccessPayload = pushPreferences.getLastRegistrationPayload();
        long timeSinceLastRegistration = (System.currentTimeMillis() - pushPreferences.getLastRegistrationTime());
        return (!payload.equals(lastSuccessPayload)) ||
                (timeSinceLastRegistration >= CHANNEL_REREGISTRATION_INTERVAL_MS);
    }

    /**
     * Checks if push registration is needed.
     *
     * @return <code>true</code> if push registration is needed, otherwise <code>false</code>.
     */
    private boolean needsPushRegistration() {
        PushPreferences pushPreferences = UAirship.shared().getPushManager().getPreferences();

        if (UAirship.getPackageInfo().versionCode != pushPreferences.getAppVersionCode()) {
            Logger.verbose("Version code changed to " + UAirship.getPackageInfo().versionCode + ". Push re-registration required.");
            return true;
        } else if (!PushManager.getSecureId(getApplicationContext()).equals(pushPreferences.getDeviceId())) {
            Logger.verbose("Device ID changed. Push re-registration required.");
            return true;
        }

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                if (UAStringUtil.isEmpty(pushPreferences.getGcmId())) {
                    return true;
                }
                Set<String> senderIds = UAirship.shared().getAirshipConfigOptions().getGCMSenderIds();
                Set<String> registeredGcmSenderIds = pushPreferences.getRegisteredGcmSenderIds();

                // Unregister if we have different registered sender ids
                if (registeredGcmSenderIds != null &&  !registeredGcmSenderIds.equals(senderIds)) {
                    Logger.verbose("GCM sender IDs changed. Push re-registration required.");
                    return true;
                }

                Logger.verbose("GCM already registered with ID: " + pushPreferences.getGcmId());
                return false;

            case UAirship.AMAZON_PLATFORM:
                if (UAStringUtil.isEmpty(pushPreferences.getAdmId())) {
                    return true;
                }

                Logger.verbose("ADM already registered with ID: " + pushPreferences.getAdmId());
                return false;
        }

        return false;
    }

    /**
     * Check if the push registration is allowed for the current platform.
     *
     * @return <code>true</code> if push registration is allowed.
     */
    private boolean isPushRegistrationAllowed() {
        AirshipConfigOptions options = UAirship.shared().getAirshipConfigOptions();

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                if (!options.isTransportAllowed(AirshipConfigOptions.GCM_TRANSPORT)) {
                    Logger.info("Unable to register for push. GCM transport type is not allowed.");
                    return false;
                }

                return true;

            case UAirship.AMAZON_PLATFORM:
                if (!options.isTransportAllowed(AirshipConfigOptions.ADM_TRANSPORT)) {
                    Logger.info("Unable to register for push. ADM transport type is not allowed.");
                    return false;
                }

                return true;

            default:
                return false;
        }
    }

    /**
     * Start the <code>Push Service</code>.
     *
     * @param context The context in which the receiver is running.
     * @param intent The intent to start the service.
     */
    static void startServiceWithWakeLock(final Context context, Intent intent) {
        Logger.debug("PushService startService");
        intent.setClass(context, PushService.class);

        // Acquire a wake lock and add the id to the intent
        intent.putExtra(EXTRA_WAKE_LOCK_ID, acquireWakeLock());

        context.startService(intent);
    }

    /**
     * Releases a wake lock.
     *
     * @param wakeLockId The id of the wake lock to release.
     */
    private static synchronized void releaseWakeLock(int wakeLockId) {
        WakeLock wakeLock = wakeLocks.get(wakeLockId);

        if (wakeLock != null) {
            wakeLocks.remove(wakeLockId);

            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    /**
     * Acquires a new wake lock.
     *
     * @return id of the wake lock.
     */
    private static synchronized int acquireWakeLock() {
        Context context = UAirship.getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UA_GCM_WAKE_LOCK");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);

        wakeLocks.append(++nextWakeLockID, wakeLock);
        return nextWakeLockID;
    }

    /**
     * Gets the channel client. Creates it if it does not exist.
     * @return The channel API client.
     */
    private ChannelAPIClient getChannelClient() {
        if (channelClient == null) {
            channelClient = new ChannelAPIClient();
        }
        return channelClient;
    }
}
