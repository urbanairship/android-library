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

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonValue;

import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * This class is the primary interface to persistent settings related to Push.
 */
class PushPreferences {
    private static final String KEY_PREFIX = "com.urbanairship.push";

    private static final String PUSH_ENABLED_KEY = KEY_PREFIX + ".PUSH_ENABLED";
    private static final String USER_NOTIFICATIONS_ENABLED_KEY = KEY_PREFIX + ".USER_NOTIFICATIONS_ENABLED";
    private static final String PUSH_TOKEN_REGISTRATION_ENABLED_KEY = KEY_PREFIX + ".PUSH_TOKEN_REGISTRATION_ENABLED";

    // As of version 5.0.0
    private static final String PUSH_ENABLED_SETTINGS_MIGRATED_KEY = KEY_PREFIX + ".PUSH_ENABLED_SETTINGS_MIGRATED";

    private static final String SOUND_ENABLED_KEY = KEY_PREFIX + ".SOUND_ENABLED";
    private static final String VIBRATE_ENABLED_KEY = KEY_PREFIX + ".VIBRATE_ENABLED";

    private static final String CHANNEL_LOCATION_KEY = KEY_PREFIX + ".CHANNEL_LOCATION";
    private static final String CHANNEL_ID_KEY = KEY_PREFIX + ".CHANNEL_ID";

    private static final String ALIAS_KEY = KEY_PREFIX + ".ALIAS";
    private static final String TAGS_KEY = KEY_PREFIX + ".TAGS";

    private static final String LAST_RECEIVED_METADATA = KEY_PREFIX + ".LAST_RECEIVED_METADATA";
    private static final String REGISTERED_GCM_SENDER_IDS = KEY_PREFIX + ".REGISTERED_GCM_SENDER_ID";


    private static final class QuietTime {
        public static final String START_HOUR_KEY = KEY_PREFIX + ".QuietTime.START_HOUR";
        public static final String START_MIN_KEY = KEY_PREFIX + ".QuietTime.START_MINUTE";
        public static final String END_HOUR_KEY = KEY_PREFIX + ".QuietTime.END_HOUR";
        public static final String END_MIN_KEY = KEY_PREFIX + ".QuietTime.END_MINUTE";
        public static final String ENABLED = KEY_PREFIX + ".QuietTime.ENABLED";
        public static final int NOT_SET_VAL = -1;
    }

    private static final String ADM_REGISTRATION_ID_KEY = KEY_PREFIX + ".ADM_REGISTRATION_ID_KEY";


    private static final String GCM_INSTANCE_ID_TOKEN_KEY = KEY_PREFIX + ".GCM_INSTANCE_ID_TOKEN_KEY";

    private static final String APP_VERSION_KEY = KEY_PREFIX + ".APP_VERSION";
    private static final String DEVICE_ID_KEY = KEY_PREFIX + ".DEVICE_ID";

    private static final String APID_KEY = KEY_PREFIX + ".APID";

    private final PreferenceDataStore preferenceDataStore;

    public PushPreferences(PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
    }

    /**
     * Migrates the old push enabled setting to the new user notifications enabled
     * setting, and enables push by default. This was introduced in version 5.0.0.
     */
    void migratePushEnabledSettings() {

        if (getPushEnabledSettingsMigrated()) {
            return;
        }

        Logger.info("Migrating push enabled preferences");

        // get old push enabled value, defaulting to false as before
        boolean oldPushEnabled = this.preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, false);

        // copy old push enabled value to user notifications enabled slot
        Logger.info("Setting user notifications enabled to " + Boolean.toString(oldPushEnabled));
        setUserNotificationsEnabled(oldPushEnabled);

        if (!oldPushEnabled) {
            Logger.info("Push is now enabled. You can continue to toggle the opt-in state by " +
                    "enabling or disabling user notifications");
        }

        // set push enabled to true
        setPushEnabled(true);

        setPushEnabledSettingsMigrated(true);
    }

    /**
     * Determines whether push enabled settings have been migrated.
     *
     * @return <code>true</code> if push enabled settings have been migrated, <code>false</code>
     * otherwise.
     */
    boolean getPushEnabledSettingsMigrated() {
        return preferenceDataStore.getBoolean(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, false);
    }

    /**
     * Sets the push enabled settings migrated flag.
     *
     * @param migrated A boolean indicating whether push enabled settings have been migrated.
     */
    void setPushEnabledSettingsMigrated(boolean migrated) {
        preferenceDataStore.put(PUSH_ENABLED_SETTINGS_MIGRATED_KEY, migrated);
    }

    /**
     * Determines whether push is enabled.
     *
     * @return <code>true</code> if push is enabled, <code>false</code> otherwise.
     */
    boolean isPushEnabled() {
        return preferenceDataStore.getBoolean(PUSH_ENABLED_KEY, true);
    }

    /**
     * Sets the push enabled flag.
     *
     * @param enabled A boolean indicating whether push is enabled.
     */
    void setPushEnabled(boolean enabled) {
        preferenceDataStore.put(PUSH_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether the GCM token or ADM ID is sent during channel registration.
     *
     * @return {@code true} if the GCM token or ADM ID is sent during channel registration,
     * {@code false} otherwise.
     */
    boolean getPushTokenRegistrationEnabled() {
        return preferenceDataStore.getBoolean(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, true);
    }

    /**
     * Sets whether the GCM token or ADM ID is sent during channel registration.
     *
     * @param enabled A boolean indicating whether the GCM token or ADM ID is sent during
     * channel registration
     */
    void setPushTokenRegistrationEnabled(boolean enabled) {
        preferenceDataStore.put(PUSH_TOKEN_REGISTRATION_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether user-facing push notifications are enabled.
     *
     * @return <code>true</code> if user push is enabled, <code>false</code> otherwise.
     */
    boolean getUserNotificationsEnabled() {
        return preferenceDataStore.getBoolean(USER_NOTIFICATIONS_ENABLED_KEY, false);
    }

    /**
     * Sets the user push enabled flag.
     *
     * @param enabled A boolean indicating whether user facing push notifications are enabled.
     */
    void setUserNotificationsEnabled(boolean enabled) {
        preferenceDataStore.put(USER_NOTIFICATIONS_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether sound is enabled.
     *
     * @return A boolean indicating whether sound is enabled.
     */
    boolean isSoundEnabled() {
        return preferenceDataStore.getBoolean(SOUND_ENABLED_KEY, true);
    }

    /**
     * Enables or disables sound.
     *
     * @param enabled A boolean indicating whether sound is enabled.
     */
    void setSoundEnabled(boolean enabled) {
        preferenceDataStore.put(SOUND_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether vibration is enabled.
     *
     * @return A boolean indicating whether vibration is enabled.
     */
    boolean isVibrateEnabled() {
        return preferenceDataStore.getBoolean(VIBRATE_ENABLED_KEY, true);
    }

    /**
     * Enables or disables vibration.
     *
     * @param enabled A boolean indicating whether vibration is enabled.
     */
    void setVibrateEnabled(boolean enabled) {
        preferenceDataStore.put(VIBRATE_ENABLED_KEY, enabled);
    }

    /**
     * Determines whether "Quiet Time" is enabled.
     *
     * @return A boolean indicating whether Quiet Time is enabled.
     */
    boolean isQuietTimeEnabled() {
        return preferenceDataStore.getBoolean(QuietTime.ENABLED, false);
    }

    /**
     * Sets the quiet time enabled.
     *
     * @param value A boolean indicating whether quiet time is enabled.
     */
    void setQuietTimeEnabled(boolean value) {
        preferenceDataStore.put(QuietTime.ENABLED, value);
    }

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @return A boolean indicating whether it is currently "Quiet Time".
     */
    boolean isInQuietTime() {
        if (!this.isQuietTimeEnabled()) {
            return false;
        }

        Calendar now = Calendar.getInstance();

        int startHr = preferenceDataStore.getInt(QuietTime.START_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int startMin = preferenceDataStore.getInt(QuietTime.START_MIN_KEY, QuietTime.NOT_SET_VAL);
        int endHr = preferenceDataStore.getInt(QuietTime.END_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int endMin = preferenceDataStore.getInt(QuietTime.END_MIN_KEY, QuietTime.NOT_SET_VAL);

        if (QuietTime.NOT_SET_VAL == startHr
                || QuietTime.NOT_SET_VAL == startMin
                || QuietTime.NOT_SET_VAL == endHr
                || QuietTime.NOT_SET_VAL == endMin) {
            // if any of the values are invalid/not set
            // quiet time is not set
            return false;
        }

        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, startHr);
        start.set(Calendar.MINUTE, startMin);
        start.set(Calendar.SECOND, 0);

        Calendar end = Calendar.getInstance();
        end.set(Calendar.HOUR_OF_DAY, endHr);
        end.set(Calendar.MINUTE, endMin);
        end.set(Calendar.SECOND, 0);

        // if the start time hasn't happened yet
        // but the end time is before the start time,
        // subtract a day
        if (start.after(now) && end.before(start)) {
            start.add(Calendar.DAY_OF_YEAR, -1);
        }

        // if end is before start time, roll over one day
        // this will not be triggered if we just set the start
        // date back by a day
        if (end.before(start)) {
            end.add(Calendar.DAY_OF_YEAR, 1);
        }

        return now.after(start) && now.before(end);
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     *
     * @return An array of two Date instances, representing the start and end of Quiet Time.
     */
    Date[] getQuietTimeInterval() {

        int startHr = preferenceDataStore.getInt(QuietTime.START_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int startMin = preferenceDataStore.getInt(QuietTime.START_MIN_KEY, QuietTime.NOT_SET_VAL);
        int endHr = preferenceDataStore.getInt(QuietTime.END_HOUR_KEY, QuietTime.NOT_SET_VAL);
        int endMin = preferenceDataStore.getInt(QuietTime.END_MIN_KEY, QuietTime.NOT_SET_VAL);

        if (startHr == QuietTime.NOT_SET_VAL || startMin == QuietTime.NOT_SET_VAL ||
                endHr == QuietTime.NOT_SET_VAL || endMin == QuietTime.NOT_SET_VAL) {
            return null;
        }

        // Grab the start date.
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, startHr);
        cal.set(Calendar.MINUTE, startMin);
        cal.set(Calendar.SECOND, 0);
        Date startDate = cal.getTime();

        // Prepare the end date.
        cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, endHr);
        cal.set(Calendar.MINUTE, endMin);
        cal.set(Calendar.SECOND, 0);

        // If the end Hour is before the start hour we assume the end time
        // is referring to an earlier hour the next day so we add a day to the
        // end time. Add one day.
        if (endHr < startHr) {
            cal.add(Calendar.DATE, 1);
        }

        Date endDate = cal.getTime();
        return new Date[] { startDate, endDate };
    }

    /**
     * Sets the Quiet Time interval.
     *
     * @param startTime A Date instance indicating when Quiet Time should start.
     * @param endTime A Date instance indicating when Quiet Time should end.
     */
    void setQuietTimeInterval(Date startTime, Date endTime) {
        Calendar startCal = Calendar.getInstance();
        startCal.setTime(startTime);
        int startHr = startCal.get(Calendar.HOUR_OF_DAY);
        int startMin = startCal.get(Calendar.MINUTE);

        Calendar endCal = Calendar.getInstance();
        endCal.setTime(endTime);
        int endHr = endCal.get(Calendar.HOUR_OF_DAY);
        int endMin = endCal.get(Calendar.MINUTE);

        preferenceDataStore.put(QuietTime.START_HOUR_KEY, startHr);
        preferenceDataStore.put(QuietTime.START_MIN_KEY, startMin);
        preferenceDataStore.put(QuietTime.END_HOUR_KEY, endHr);
        preferenceDataStore.put(QuietTime.END_MIN_KEY, endMin);
    }

    /**
     * Returns the device's associated alias.
     *
     * @return An alias String, null if not present.
     */
    String getAlias() {
        return preferenceDataStore.getString(ALIAS_KEY, null);
    }

    /**
     * Sets the device's associated alias in persistent settings. This call is not typically used at
     * the application level as it does not perform an update on Urban Airship's servers. See
     * {@link com.urbanairship.push.PushManager}
     * for methods that update both the server and persistent settings.
     *
     * @param value An alias String.
     * @see com.urbanairship.push.PushManager#setAliasAndTags(String, Set)
     * @see com.urbanairship.push.PushManager#setAlias(String)
     */
    void setAlias(String value) {
        preferenceDataStore.put(ALIAS_KEY, value);
    }

    /**
     * Returns the set of tags associated with the device.
     *
     * @return A Set of tag Strings.
     */
    Set<String> getTags() {
        Set<String> tags = new HashSet<>();

        JsonValue jsonValue = preferenceDataStore.getJsonValue(TAGS_KEY);

        if (jsonValue.isJsonList()) {
            for (JsonValue tag : jsonValue.getList()) {
                if (tag.isString()) {
                    tags.add(tag.getString());
                }
            }
        }

        return tags;
    }

    /**
     * Stores a set of tags to be associated with the device in persistent settings.
     * See {@link com.urbanairship.push.PushManager} for methods that update both the
     * server and persistent settings.
     *
     * @param tags A Set of tag Strings.
     * @see com.urbanairship.push.PushManager#setAliasAndTags(String, Set)
     * @see com.urbanairship.push.PushManager#setTags(Set)
     */
    void setTags(Set<String> tags) {
        if (tags == null || tags.isEmpty()) {
            preferenceDataStore.remove(TAGS_KEY);
        } else {
            preferenceDataStore.put(TAGS_KEY, JsonValue.wrapOpt(tags));
        }
    }

    /**
     * Stores a GCM Instance ID token.
     *
     * @param token The GCM Instance ID token.
     */
    void setGcmToken(String token) {
        preferenceDataStore.put(GCM_INSTANCE_ID_TOKEN_KEY, token);
    }

    /**
     * Returns the GCM Instance ID token.
     */
    String getGcmToken() {
        return preferenceDataStore.getString(GCM_INSTANCE_ID_TOKEN_KEY, null);
    }

    /**
     * Returns the ADM registration ID currently associated with the device.
     *
     * @return An ADM registration ID String.
     */
    String getAdmId() {
        return preferenceDataStore.getString(ADM_REGISTRATION_ID_KEY, null);
    }

    /**
     * Stores an ADM registration ID to be associated with the device.
     *
     * @param id An ADM registration ID String.
     */
    void setAdmId(String id) {
        preferenceDataStore.put(ADM_REGISTRATION_ID_KEY, id);
    }

    /**
     * Store the app version associated with a Registration ID.
     *
     * @param appVersion The app version string.
     */
    void setAppVersionCode(final int appVersion) {
        preferenceDataStore.put(APP_VERSION_KEY, appVersion);
    }

    /**
     * Returns the app version associated with the current Registration ID.
     *
     * @return The app version string, or -1 if not found.
     */
    int getAppVersionCode() {
        return preferenceDataStore.getInt(APP_VERSION_KEY, -1);
    }

    /**
     * Store the device ID associated with the current Registration ID.
     *
     * @param deviceId The device ID string
     */
    void setDeviceId(final String deviceId) {
        preferenceDataStore.put(DEVICE_ID_KEY, deviceId);
    }

    /**
     * Returns the device ID associated with the current Registration ID.
     *
     * @return The device ID string, or <code>null</code> if not found.
     */
    String getDeviceId() {
        return preferenceDataStore.getString(DEVICE_ID_KEY, null);
    }

    /**
     * Returns the channel location currently associated with the device.
     *
     * @return A channel location URL.
     */
    String getChannelLocation() {
        return preferenceDataStore.getString(CHANNEL_LOCATION_KEY, null);
    }

    /**
     * Stores a channel location to be associated with the device.
     *
     * @param channelLocation A channel location URL.
     */
    void setChannelLocation(String channelLocation) {
        preferenceDataStore.put(CHANNEL_LOCATION_KEY, channelLocation);
    }

    /**
     * Returns the device's associated Channel ID
     *
     * @return a Channel ID string.
     */
    String getChannelId() {
        return preferenceDataStore.getString(CHANNEL_ID_KEY, null);
    }

    /**
     * Sets the device's associated Channel ID.
     *
     * @param value a Channel ID string.
     */
    void setChannelId(String value) {
        preferenceDataStore.put(CHANNEL_ID_KEY, value);
    }

    /**
     * Return the device's existing APID
     *
     * @return an APID string or null if it doesn't exist.
     */
    String getApid() {
        return preferenceDataStore.getString(APID_KEY, null);
    }

    /**
     * Store the send metadata from the last received push.
     *
     * @param sendMetadata The send metadata string.
     */
    void setLastReceivedMetadata(String sendMetadata) {
        preferenceDataStore.put(LAST_RECEIVED_METADATA, sendMetadata);
    }

    /**
     * Returns the send metadata of the last received push.
     *
     * @return The send metadata from the last received push, or null if not found.
     */
    String getLastReceivedMetadata() {
        return preferenceDataStore.getString(LAST_RECEIVED_METADATA, null);
    }

    /**
     * Sets the registered sender ID.
     *
     * @param senderId The registered sender ID.
     */
    void setRegisteredGcmSenderId(String senderId) {
        preferenceDataStore.put(REGISTERED_GCM_SENDER_IDS, senderId);
    }

    /**
     * Gets the registered sender ID.
     *
     * @return The registered sender ID.
     */
    String getRegisteredGcmSenderId() {
        return preferenceDataStore.getString(REGISTERED_GCM_SENDER_IDS, null);
    }

}
