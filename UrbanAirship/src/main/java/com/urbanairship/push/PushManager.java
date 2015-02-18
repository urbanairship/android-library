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

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.urbanairship.BaseManager;
import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.Situation;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.push.ian.InAppNotification;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.util.UAStringUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * This class is the primary interface for customizing the display and behavior
 * of incoming push notifications.
 */
public class PushManager extends BaseManager {

    /**
     * Action sent as a broadcast when a push message is received.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE}
     */
    public static final String ACTION_PUSH_RECEIVED = "com.urbanairship.push.RECEIVED";

    /**
     * Action sent as a broadcast when a notification is opened.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE},
     * {@link #EXTRA_NOTIFICATION_BUTTON_ID},
     * {@link #EXTRA_NOTIFICATION_BUTTON_FOREGROUND}
     */
    public static final String ACTION_NOTIFICATION_OPENED = "com.urbanairship.push.OPENED";

    /**
     * Action sent as a broadcast when a notification is dismissed.
     * <p/>
     * Extras:
     * {@link #EXTRA_NOTIFICATION_ID},
     * {@link #EXTRA_PUSH_MESSAGE}
     */
    public static final String ACTION_NOTIFICATION_DISMISSED = "com.urbanairship.push.DISMISSED";

    /**
     * Action sent as a broadcast when a channel registration succeeds.
     * <p/>
     * Extras:
     * {@link #EXTRA_CHANNEL_ID}
     */
    public static final String ACTION_CHANNEL_UPDATED = "com.urbanairship.push.CHANNEL_UPDATED";

    /**
     * The notification ID extra contains the ID of the notification placed in the
     * <code>NotificationManager</code> by the library.
     * <p/>
     * If a <code>Notification</code> was not created, the extra will not be included.
     */
    public static final String EXTRA_NOTIFICATION_ID = "com.urbanairship.push.NOTIFICATION_ID";

    /**
     * The push message extra.
     */
    public static final String EXTRA_PUSH_MESSAGE = "com.urbanairship.push.EXTRA_PUSH_MESSAGE";

    /**
     * The interactive notification action button identifier extra.
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_ID = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ID";

    /**
     * The flag indicating if the interactive notification action button is background or foreground.
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_FOREGROUND = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_FOREGROUND";

    /**
     * Extra used to indicate an error in channel registration.
     */
    public static final String EXTRA_ERROR = "com.urbanairship.push.EXTRA_ERROR";

    /**
     * The channel ID extra.
     */
    public static final String EXTRA_CHANNEL_ID = "com.urbanairship.push.EXTRA_CHANNEL_ID";

    /**
     * This intent action indicates that a push notification has been opened.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_OPENED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_OPENED_PROXY";

    /**
     * This intent action indicates that a push notification button has been opened.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_BUTTON_OPENED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_BUTTON_OPENED_PROXY";

    /**
     * This intent action indicates that a push notification button has been dismissed.
     *
     * @hide
     */
    public static final String ACTION_NOTIFICATION_DISMISSED_PROXY = "com.urbanairship.ACTION_NOTIFICATION_DISMISSED_PROXY";

    /**
     * The CONTENT_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_CONTENT_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_CONTENT_INTENT";

    /**
     * The DELETE_INTENT extra is an optional intent that the notification builder can
     * supply on the notification. If set, the intent will be pulled from the notification,
     * stored as part of the supplied UA intent, and then sent inside the UA core receiver.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_DELETE_INTENT = "com.urbanairship.push.EXTRA_NOTIFICATION_DELETE_INTENT";

    /**
     * The description of the notification action button.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION = "com.urbanairship.push.EXTRA_NOTIFICATION_ACTION_BUTTON_DESCRIPTION";

    /**
     * The actions payload for the notification action button.
     *
     * @hide
     */
    public static final String EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD = "com.urbanairship.push.EXTRA_NOTIFICATION_BUTTON_ACTIONS_PAYLOAD";

    private static final int MAX_TAG_LENGTH = 127;
    private static final int MAX_CANONICAL_IDS = 10;
    private static final int RICH_PUSH_REFRESH_WAIT_TIME_MS = 60000; // 1 minute

    private String UA_NOTIFICATION_BUTTON_GROUP_PREFIX = "ua_";

    //singleton stuff
    private NotificationFactory notificationFactory;
    private Map<String, NotificationActionButtonGroup> actionGroupMap = new HashMap<>();
    private boolean deviceTagsEnabled = true;
    private NamedUser namedUser;

    PushPreferences preferences;
    NotificationManagerCompat notificationManager;

    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getPushManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @hide
     */
    public PushManager(Context context, PreferenceDataStore preferenceDataStore) {
        this(context, new PushPreferences(preferenceDataStore), new NamedUser(preferenceDataStore), NotificationManagerCompat.from(context));
    }

    PushManager(Context context, PushPreferences preferences, NamedUser namedUser, NotificationManagerCompat notificationManager) {
        this.notificationManager = notificationManager;
        this.preferences = preferences;
        this.notificationFactory = new DefaultNotificationFactory(context);
        this.namedUser = namedUser;

        if (Logger.logLevel < Log.ASSERT && !UAStringUtil.isEmpty(getChannelId())) {
            Log.d(UAirship.getAppName() + " Channel ID", getChannelId());
        }

        actionGroupMap.putAll(NotificationActionButtonGroupFactory.createUrbanAirshipGroups());
    }

    @Override
    protected void init() {

        this.preferences.migratePushEnabledSettings();

        // Start registration
        Intent i = new Intent(UAirship.getApplicationContext(), PushService.class);
        i.setAction(PushService.ACTION_START_REGISTRATION);
        UAirship.getApplicationContext().startService(i);

        // Start named user update
        this.namedUser.startUpdateService();
    }

    /**
     * Returns the shared PushManager singleton instance. This call will block unless
     * UAirship is ready.
     *
     * @return The shared PushManager instance.
     * @deprecated As of 5.0.0. Use {@link com.urbanairship.UAirship#getPushManager()}} instead.
     */
    @Deprecated
    public static PushManager shared() {
        return UAirship.shared().getPushManager();
    }

    /**
     * Enables or disables push notifications.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether push is enabled.
     */
    public void setPushEnabled(boolean enabled) {
        preferences.setPushEnabled(enabled);
        updateRegistration();
    }

    /**
     * Determines whether push is enabled.
     *
     * @return <code>true</code> if push is enabled, <code>false</code> otherwise.
     * This defaults to false, and must be explicitly set by the app.
     */
    public boolean isPushEnabled() {
        return preferences.isPushEnabled();
    }

    /**
     * Enables or disables user notifications.
     *
     * User notifications are push notifications that contain an alert message and are
     * intended to be shown to the user.
     *
     * @note This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when a user preference has changed.
     *
     * @param enabled A boolean indicating whether user push is enabled.
     */
    public void setUserNotificationsEnabled(boolean enabled) {
        preferences.setUserNotificationsEnabled(enabled);
        updateRegistration();
    }

    /**
     * Determines whether user-facing push notifications are enabled.
     *
     * @return <code>true</code> if user push is enabled, <code>false</code> otherwise.
     */
    public boolean getUserNotificationsEnabled() {
        return preferences.getUserNotificationsEnabled();
    }


    /**
     * Sets the notification factory used when push notifications are received.
     * <p/>
     * Specify a notification factory here to customize the display
     * of a push notification's Custom Expanded Views in the
     * Android Notification Manager.
     * <p/>
     * If <code>null</code>, push notifications will not be displayed by the
     * library.
     *
     * @param factory The notification factory
     * @see com.urbanairship.push.notifications.NotificationFactory
     * @see com.urbanairship.push.notifications.DefaultNotificationFactory
     * @see com.urbanairship.push.notifications.SystemNotificationFactory
     * @see com.urbanairship.push.notifications.CustomLayoutNotificationFactory
     */
    public void setNotificationFactory(NotificationFactory factory) {
        notificationFactory = factory;
    }

    /**
     * Returns the current notification factory.
     *
     * @return The current notification factory.
     */
    public NotificationFactory getNotificationFactory() {
        return notificationFactory;
    }

    /**
     * Returns the <code>PushPreferences</code> singleton for this application.
     *
     * @return The PushPreferences
     */
    PushPreferences getPreferences() {
        return preferences;
    }

    /**
     * Sets both the alias and tags for this channel and updates the server.
     * <p/>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p/>
     *
     * @param alias The desired alias, <code>null</code> to remove
     * @param tags The desired set of tags, must be non-null
     * @see #setAlias(String)
     * @see #setTags(Set)
     */
    public void setAliasAndTags(String alias, Set<String> tags) {
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }

        Set<String> normalizedTags = normalizeTags(tags);

        // only update server w/ registration call if
        // at least one of the values has changed
        boolean updateServer = false;

        //check for change to alias
        if (!UAStringUtil.equals(alias, preferences.getAlias())) {
            preferences.setAlias(alias);
            updateServer = true;
        }

        //check for change to tag set
        if (!normalizedTags.equals(preferences.getTags())) {
            preferences.setTags(normalizedTags);
            updateServer = true;
        }

        if (updateServer) {
            updateRegistration();
        }
    }

    /**
     * Determines whether the app is capable of receiving push,
     * meaning whether a GCM or ADM registration ID is present.
     *
     * @return <code>true</code> if push is available, <code>false</code> otherwise.
     */
    public boolean isPushAvailable() {
        switch (UAirship.shared().getPlatformType()) {
            case UAirship.AMAZON_PLATFORM:
                return !UAStringUtil.isEmpty(getPreferences().getAdmId());
            case UAirship.ANDROID_PLATFORM:
                return !UAStringUtil.isEmpty(getPreferences().getGcmId());
        }
        return false;
    }

    /**
     * Returns if the application is currently opted in for push.
     *
     * @return <code>true</code> if opted in for push.
     */
    public boolean isOptIn() {
        return isPushEnabled() && isPushAvailable() && getUserNotificationsEnabled();
    }

    /**
     * Returns the next channel registration payload
     *
     * @return The ChannelRegistrationPayload payload
     */
    ChannelRegistrationPayload getNextChannelRegistrationPayload() {
        ChannelRegistrationPayload.Builder builder = new ChannelRegistrationPayload.Builder()
                .setAlias(getAlias())
                .setTags(getDeviceTagsEnabled(), getTags())
                .setOptIn(isOptIn())
                .setBackgroundEnabled(isPushEnabled() && isPushAvailable())
                .setUserId(UAirship.shared().getRichPushManager().getRichPushUser().getId())
                .setApid(preferences.getApid());

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                builder.setDeviceType("android")
                       .setPushAddress(preferences.getGcmId());
                break;
            case UAirship.AMAZON_PLATFORM:
                builder.setDeviceType("amazon")
                       .setPushAddress(preferences.getAdmId());
                break;
        }

        return builder.build();
    }


    /**
     * Update registration.
     */
    public void updateRegistration() {
        Context ctx = UAirship.getApplicationContext();
        Intent i = new Intent(ctx, PushService.class);
        i.setAction(PushService.ACTION_UPDATE_REGISTRATION);
        ctx.startService(i);
    }

    /**
     * Set the alias for the channel and update the server.
     * <p/>
     * If you are setting both the alias and tags at the same time, you should
     * use {@link #setAliasAndTags(String, Set)} to avoid making an extra
     * network call.
     * <p/>
     * Refer to the
     * <a href="https://docs.urbanairship.com/build/android_features.html#aliases">Alias</a>
     * for more information on the use of aliases.
     *
     * @param alias The alias, <code>null</code> to remove
     */
    public void setAlias(String alias) {
        if (!UAStringUtil.equals(alias, preferences.getAlias())) {
            preferences.setAlias(alias);
            updateRegistration();
        }
    }

    /**
     * Set tags for the channel and update the server.
     * <p/>
     * Tags should be URL-safe with a length greater than 0 and less than 127 characters. If your
     * tag includes whitespace or special characters, we recommend URL encoding the string.
     * <p/>
     * To clear the current set of tags, pass an empty set to this method.
     * <p/>
     * If you are setting both the alias and tags at the same time, you should
     * use {@link #setAliasAndTags(String, Set)} to avoid making an extra
     * network call.
     * <p/>
     * Refer to the <a href="https://docs.urbanairship.com/build/android_features.html#tags">Tag API</a> for
     * more information.
     *
     * @param tags A set of tag strings.
     */
    public void setTags(Set<String> tags) {
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }

        Set<String> normalizedTags = normalizeTags(tags);
        if (!normalizedTags.equals(preferences.getTags())) {
            preferences.setTags(normalizedTags);
            updateRegistration();
        }
    }

    /**
     * Normalizes a set of tags. Each tag will be trimmed of white space and any tag that
     * is empty, null, or exceeds {@link #MAX_TAG_LENGTH} will be dropped.
     *
     * @param tags The set of tags to normalize.
     * @return The set of normalized, valid tags.
     */
    private Set<String> normalizeTags(Set<String> tags) {
        if (tags == null) {
            return null;
        }

        Set<String> normalizedTags = new HashSet<>();

        for (String tag : tags) {
            if (tag == null) {
                Logger.debug("PushManager - Null tag was removed from set.");
                continue;
            }

            tag = tag.trim();
            if (tag.length() <= 0 || tag.length() > MAX_TAG_LENGTH) {
                Logger.error("Tag with zero or greater than max length was removed from set: " + tag);
                continue;
            }

            normalizedTags.add(tag);
        }

        return normalizedTags;
    }

    /**
     * Returns the current alias for this application's channel.
     *
     * @return The string alias, or an empty string if one is not set.
     */
    public String getAlias() {
        return preferences.getAlias();
    }

    /**
     * Returns the current named user.
     *
     * @return The named user.
     */
    public NamedUser getNamedUser() {
        return namedUser;
    }

    /**
     * Returns the current set of tags.
     * <p/>
     * An empty set indicates that no tags are set on this channel.
     *
     * @return The current set of tags.
     */
    public Set<String> getTags() {
        Set<String> tags = preferences.getTags();
        Set<String> normalizedTags = this.normalizeTags(tags);

        //to prevent the getTags call from constantly logging tag set failures, sync tags
        if (tags.size() != normalizedTags.size()) {
            this.setTags(normalizedTags);
        }
        return normalizedTags;
    }

    /**
     * Returns the currently registered GCM ID.
     *
     * @return A GCM identifier string, or null if not present.
     */
    public String getGcmId() {
        return preferences.getGcmId();
    }

    /**
     * Returns the currently registered ADM ID.
     *
     * @return An ADM identifier string, or null if not present.
     */
    public String getAdmId() {
        return preferences.getAdmId();
    }

    /**
     * Determines whether tags are enabled on the device.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if tags are enabled on the device, <code>false</code> otherwise.
     */
    public boolean getDeviceTagsEnabled() {
        return deviceTagsEnabled;
    }

    /**
     * Sets whether tags are enabled on the device. The default value is <code>true</code>.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     *
     * @param enabled A boolean indicating whether tags are enabled on the device.
     */
    public void setDeviceTagsEnabled(boolean enabled) {
        deviceTagsEnabled = enabled;
    }

    /**
     * Determines whether sound is enabled.
     *
     * @return A boolean indicated whether sound is enabled.
     */
    public boolean isSoundEnabled() {
        return preferences.isSoundEnabled();
    }

    /**
     * Enables or disables sound.
     *
     * @param enabled A boolean indicating whether sound is enabled.
     */
    public void setSoundEnabled(boolean enabled) {
        preferences.setSoundEnabled(enabled);
    }

    /**
     * Determines whether vibration is enabled.
     *
     * @return A boolean indicating whether vibration is enabled.
     */
    public boolean isVibrateEnabled() {
        return preferences.isVibrateEnabled();
    }

    /**
     * Enables or disables vibration.
     *
     * @param enabled A boolean indicating whether vibration is enabled.
     */
    public void setVibrateEnabled(boolean enabled) {
        preferences.setVibrateEnabled(enabled);
    }

    /**
     * Determines whether "Quiet Time" is enabled.
     *
     * @return A boolean indicating whether Quiet Time is enabled.
     */
    public boolean isQuietTimeEnabled() {
        return preferences.isQuietTimeEnabled();
    }

    /**
     * Sets the quiet time enabled.
     *
     * @param enabled A boolean indicating whether quiet time is enabled.
     */
    public void setQuietTimeEnabled(boolean enabled) {
        preferences.setQuietTimeEnabled(enabled);
    }

    /**
     * Determines whether we are currently in the middle of "Quiet Time".  Returns false if Quiet Time is disabled,
     * and evaluates whether or not the current date/time falls within the Quiet Time interval set by the user.
     *
     * @return A boolean indicating whether it is currently "Quiet Time".
     */
    public boolean isInQuietTime() {
        return preferences.isInQuietTime();
    }

    /**
     * Returns the Quiet Time interval currently set by the user.
     *
     * @return An array of two Date instances, representing the start and end of Quiet Time.
     */
    public Date[] getQuietTimeInterval() {
        return preferences.getQuietTimeInterval();
    }

    /**
     * Returns the send id of the last received push.
     *
     * @return The send id from the last received push, or null if not found.
     */
    public String getLastReceivedSendId() {
        return preferences.getLastReceivedSendId();
    }

    /**
     * Sets the Quiet Time interval.
     *
     * @param startTime A Date instance indicating when Quiet Time should start.
     * @param endTime A Date instance indicating when Quiet Time should end.
     */
    public void setQuietTimeInterval(Date startTime, Date endTime) {
        preferences.setQuietTimeInterval(startTime, endTime);
    }

    /**
     * Register a notification action group under the given name.
     * <p/>
     * The provided notification builders will automatically add the actions to the
     * notification when a message is received with a group specified under the
     * {@link com.urbanairship.push.PushMessage#EXTRA_INTERACTIVE_TYPE}
     * key.
     *
     * @param id The id of the action group.
     * @param group The notification action group.
     */
    public void addNotificationActionButtonGroup(String id, NotificationActionButtonGroup group) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.warn("Unable to add any notification button groups that starts with the reserved Urban Airship prefix " + UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.put(id, group);
    }

    /**
     * Removes the notification button group under the given name.
     *
     * @param id The id of the button group to remove.
     */
    public void removeNotificationActionButtonGroup(String id) {
        if (id.startsWith(UA_NOTIFICATION_BUTTON_GROUP_PREFIX)) {
            Logger.error("Unable to remove any reserved Urban Airship actions groups that begin with " + UA_NOTIFICATION_BUTTON_GROUP_PREFIX);
            return;
        }

        actionGroupMap.remove(id);
    }

    /**
     * Returns the notification action group that is registered under the given name.
     *
     * @param id The id of the action group.
     * @return The notification action group.
     */
    public NotificationActionButtonGroup getNotificationActionGroup(String id) {
        return actionGroupMap.get(id);
    }

    private static void createPushArrivedEvent(String sendId) {
        if (UAStringUtil.isEmpty(sendId)) {
            sendId = UUID.randomUUID().toString();
        }
        UAirship.shared().getAnalytics().addEvent(new PushArrivedEvent(sendId));
    }


    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return <code>false</code> if the ID exists in the history, otherwise <code>true</code>.
     */
    private boolean isUniqueCanonicalId(String canonicalId) {
        if (canonicalId == null) {
            return true;
        }

        // add the value - return
        List<String> canonicalIds = preferences.getCanonicalIds();

        if (canonicalIds.contains(canonicalId)) {
            return false;
        }

        canonicalIds.add(canonicalId);
        if (canonicalIds.size() > MAX_CANONICAL_IDS) {
            List<String> subList = canonicalIds.subList(canonicalIds.size() - MAX_CANONICAL_IDS, canonicalIds.size());
            preferences.setCanonicalIds(subList);
        } else {
            preferences.setCanonicalIds(canonicalIds);
        }

        return true;
    }

    /**
     * Broadcasts an intent to notify the host application of a push message received, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param message The message that created the notification
     * @param notificationId The id of the messages created notification
     */
    private void sendPushReceivedBroadcast(PushMessage message, Integer notificationId) {
        Intent intent = new Intent(ACTION_PUSH_RECEIVED)
                .putExtra(EXTRA_PUSH_MESSAGE, message)
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (notificationId != null) {
            intent.putExtra(EXTRA_NOTIFICATION_ID, notificationId.intValue());
        }

        UAirship.getApplicationContext().sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Broadcasts an intent to notify the host application of a registration finished, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param isSuccess A boolean indicating whether registration succeeded or not.
     */
    void sendRegistrationFinishedBroadcast(boolean isSuccess) {
        Intent intent = new Intent(ACTION_CHANNEL_UPDATED)
                .putExtra(EXTRA_CHANNEL_ID, getChannelId())
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (!isSuccess) {
            intent.putExtra(EXTRA_ERROR, true);
        }

        UAirship.getApplicationContext().sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * If alerts are enabled and the application is installed, this method
     * displays an entry in the Notification bar (if a message is present) and
     * sends out an intent to the application containing its data.
     *
     * @param message Message to deliver
     */
    void deliverPush(PushMessage message) {
        if (!isPushEnabled()) {
            Logger.info("Received a push when push is disabled! Ignoring.");
            return;
        }

        if (!isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return;
        }

        preferences.setLastReceivedSendId(message.getSendId());
        createPushArrivedEvent(message.getSendId());

        // Run any actions for the push
        ActionService.runActionsPayload(UAirship.getApplicationContext(), message.getActionsPayload(), Situation.PUSH_RECEIVED, message);

        if (message.isPing()) {
            Logger.verbose("PushManager - Received UA Ping");
            return;
        }

        if (message.isExpired()) {
            Logger.debug("PushManager - Notification expired, ignoring.");
            return;
        }

        InAppNotification inAppNotification = message.getInAppNotification();
        if (inAppNotification != null) {
            Logger.debug("PushManager - Received a Push with an InAppNotification.");
            UAirship.shared().getInAppNotificationManager().setPendingNotification(inAppNotification);
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId())) {
            Logger.debug("PushManager - Received a Rich Push.");
            refreshRichPushMessages();
        }

        Integer id = show(message, getNotificationFactory());
        sendPushReceivedBroadcast(message, id);
    }

    /**
     * Get the Channel ID
     *
     * @return A Channel ID string
     */
    public String getChannelId() {
        return preferences.getChannelId();
    }

    /**
     * Sets the Channel ID and channel location.
     * Also update the user.
     *
     * @param channelId The channel ID as a string.
     * @param channelLocation The channel location as a URL.
     */
    void setChannel(String channelId, String channelLocation) {
        preferences.setChannelId(channelId);
        preferences.setChannelLocation(channelLocation);

        UAirship.shared().getRichPushManager().updateUser();
    }

    /**
     * Sets the registered GCM ID.
     *
     * @param gcmId A GCM identifier string.
     */
    void setGcmId(String gcmId) {
        preferences.setAppVersionCode(UAirship.getPackageInfo().versionCode);
        preferences.setGcmId(gcmId);
        preferences.setDeviceId(getSecureId(UAirship.getApplicationContext()));
    }

    /**
     * Sets the registered ADM ID.
     *
     * @param admId An ADM identifier string.
     */
    void setAdmId(String admId) {
        preferences.setAppVersionCode(UAirship.getPackageInfo().versionCode);
        preferences.setAdmId(admId);
        preferences.setDeviceId(getSecureId(UAirship.getApplicationContext()));
    }

    /**
     * Helper method that blocks while the rich push messages are refreshing
     */
    private void refreshRichPushMessages() {
        final Semaphore semaphore = new Semaphore(0);
        UAirship.shared().getRichPushManager().refreshMessages(new RichPushManager.RefreshMessagesCallback() {
            @Override
            public void onRefreshMessages(boolean success) {
                semaphore.release();
            }
        });

        try {
            semaphore.tryAcquire(RICH_PUSH_REFRESH_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Logger.warn("Interrupted while waiting for rich push messages to refresh");
        }
    }


    /**
     * Builds and displays the notification.
     *
     * @param message The push message.
     * @return The notification ID.
     */
    private Integer show(PushMessage message, NotificationFactory builder) {
        if (message == null || builder == null || !getUserNotificationsEnabled()) {
            return null;
        }

        Integer notificationId;
        Notification notification;
        Context context = UAirship.getApplicationContext();

        try {
            notificationId = builder.getNextId(message);
            notification = builder.createNotification(message, notificationId);
        } catch (Exception e) {
            Logger.error("Unable to create and display notification.", e);
            return null;
        }

        if (notification != null) {
            if (!isVibrateEnabled() || isInQuietTime()) {
                // Remove both the vibrate and the DEFAULT_VIBRATE flag
                notification.vibrate = null;
                notification.defaults &= ~Notification.DEFAULT_VIBRATE;
            }

            if (!isSoundEnabled() || isInQuietTime()) {
                // Remove both the sound and the DEFAULT_SOUND flag
                notification.sound = null;
                notification.defaults &= ~Notification.DEFAULT_SOUND;
            }

            Intent contentIntent = new Intent(context, CoreReceiver.class)
                    .setAction(ACTION_NOTIFICATION_OPENED_PROXY)
                    .addCategory(UUID.randomUUID().toString())
                    .putExtra(EXTRA_PUSH_MESSAGE, message)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId);

            // If the notification already has an intent, add it to the extras to be sent later
            if (notification.contentIntent != null) {
                contentIntent.putExtra(EXTRA_NOTIFICATION_CONTENT_INTENT, notification.contentIntent);
            }

            Intent deleteIntent = new Intent(context, CoreReceiver.class)
                    .setAction(ACTION_NOTIFICATION_DISMISSED_PROXY)
                    .addCategory(UUID.randomUUID().toString())
                    .putExtra(EXTRA_PUSH_MESSAGE, message)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId);

            if (notification.deleteIntent != null) {
                deleteIntent.putExtra(EXTRA_NOTIFICATION_DELETE_INTENT, notification.deleteIntent);
            }

            notification.contentIntent = PendingIntent.getBroadcast(context, 0, contentIntent, 0);
            notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);

            Logger.info("Posting notification " + notification + " with ID " + notificationId);
            notificationManager.notify(notificationId, notification);

            return notificationId;
        }

        return null;
    }

    /**
     * Gets the Android secure ID.
     * @param context The application context.
     * @return The Android secure ID.
     *
     * @Hide
     */
    static String getSecureId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
