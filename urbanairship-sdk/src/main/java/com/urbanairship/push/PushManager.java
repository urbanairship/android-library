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
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseManager;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.push.notifications.DefaultNotificationFactory;
import com.urbanairship.push.notifications.NotificationActionButtonGroup;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.util.UAStringUtil;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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


    private final String UA_NOTIFICATION_BUTTON_GROUP_PREFIX = "ua_";

    /**
     * The default tag group.
     */
    private final String DEFAULT_TAG_GROUP = "device";

    //singleton stuff
    private NotificationFactory notificationFactory;
    private final Map<String, NotificationActionButtonGroup> actionGroupMap = new HashMap<>();
    private boolean channelTagRegistrationEnabled = true;
    private final NamedUser namedUser;
    private final PushPreferences preferences;
    private final AirshipConfigOptions configOptions;
    private boolean channelCreationDelayEnabled;


    /**
     * Creates a PushManager. Normally only one push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getPushManager()}.
     *
     * @param context Application context
     * @param preferenceDataStore The preferences data store.
     * @param configOptions The airship config options.
     * @hide
     */
    public PushManager(Context context, PreferenceDataStore preferenceDataStore, AirshipConfigOptions configOptions) {
        this(context, new PushPreferences(preferenceDataStore), new NamedUser(preferenceDataStore), configOptions);
    }

    PushManager(Context context, PushPreferences preferences, NamedUser namedUser, AirshipConfigOptions configOptions) {
        this.preferences = preferences;
        this.notificationFactory = new DefaultNotificationFactory(context);
        this.namedUser = namedUser;
        this.configOptions = configOptions;

        if (Logger.logLevel < Log.ASSERT && !UAStringUtil.isEmpty(getChannelId())) {
            Log.d(UAirship.getAppName() + " Channel ID", getChannelId());
        }

        actionGroupMap.putAll(NotificationActionButtonGroupFactory.createUrbanAirshipGroups());
    }

    @Override
    protected void init() {
        this.preferences.migratePushEnabledSettings();

        if (preferences.getChannelId() == null && configOptions.channelCreationDelayEnabled) {
            channelCreationDelayEnabled = true;
        } else {
            channelCreationDelayEnabled = false;
        }

        // Start registration
        Intent registrationIntent = new Intent(UAirship.getApplicationContext(), PushService.class)
                .setAction(PushService.ACTION_START_REGISTRATION);

        UAirship.getApplicationContext().startService(registrationIntent);

        // If we have a channel already check for pending tags
        if (getChannelId() != null) {
            startUpdateTagsService();
        }


        // Start named user update
        this.namedUser.startUpdateService();

        // Update named user tags if we have a named user
        if (namedUser.getId() != null) {
            this.namedUser.startUpdateTagsService();
        }

    }

    /**
     * Enables channel creation if channel creation has been delayed.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
     * repeatedly. It is only necessary to call this when channelCreationDelayEnabled has been
     * set to <code>true</code> in the airship config.
     *
     */
    public void enableChannelCreation() {
        if (isChannelCreationDelayEnabled()) {
            channelCreationDelayEnabled = false;
            updateRegistration();
        }
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
     * <p/>
     * User notifications are push notifications that contain an alert message and are
     * intended to be shown to the user.
     * <p/>
     * This setting is persisted between application starts, so there is no need to call this
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
    public void setNotificationFactory(@NonNull NotificationFactory factory) {
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
    public void setAliasAndTags(@Nullable String alias, @NonNull Set<String> tags) {
        //noinspection ConstantConditions
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }

        Set<String> normalizedTags = TagUtils.normalizeTags(tags);

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
        if (getPushTokenRegistrationEnabled()) {
            switch (UAirship.shared().getPlatformType()) {
                case UAirship.AMAZON_PLATFORM:
                    return !UAStringUtil.isEmpty(getPreferences().getAdmId());
                case UAirship.ANDROID_PLATFORM:
                    return !UAStringUtil.isEmpty(getPreferences().getGcmToken());
            }
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
                .setTags(getChannelTagRegistrationEnabled(), getTags())
                .setOptIn(isOptIn())
                .setBackgroundEnabled(isPushEnabled() && isPushAvailable())
                .setUserId(UAirship.shared().getRichPushManager().getRichPushUser().getId())
                .setApid(preferences.getApid());

        switch (UAirship.shared().getPlatformType()) {
            case UAirship.ANDROID_PLATFORM:
                builder.setDeviceType("android");
                if (getPushTokenRegistrationEnabled()) {
                    builder.setPushAddress(getGcmToken());
                }
                break;
            case UAirship.AMAZON_PLATFORM:
                builder.setDeviceType("amazon");
                if (getPushTokenRegistrationEnabled()) {
                    builder.setPushAddress(getAdmId());
                }
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
        i.setAction(PushService.ACTION_UPDATE_CHANNEL_REGISTRATION);
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
    public void setAlias(@Nullable String alias) {
        if (alias != null) {
            alias = alias.trim();
        }

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
    public void setTags(@NonNull Set<String> tags) {
        //noinspection ConstantConditions
        if (tags == null) {
            throw new IllegalArgumentException("Tags must be non-null.");
        }

        Set<String> normalizedTags = TagUtils.normalizeTags(tags);
        if (!normalizedTags.equals(preferences.getTags())) {
            preferences.setTags(normalizedTags);
            updateRegistration();
        }
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
    @NonNull
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
    @NonNull
    public Set<String> getTags() {
        Set<String> tags = preferences.getTags();
        Set<String> normalizedTags = TagUtils.normalizeTags(tags);

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
     * @deprecated Marked to be removed in 7.0.0. The GCM security token for {@link com.urbanairship.AirshipConfigOptions#gcmSender}
     * is available with {@link #getGcmToken}.
     */
    @Deprecated
    public String getGcmId() {
        return preferences.getGcmId();
    }

    /**
     * Returns the currently registered ADM ID.
     *
     * @return An ADM identifier string, or null if not present.
     */
    @Nullable
    public String getAdmId() {
        return preferences.getAdmId();
    }

    /**
     * Determines whether tags are enabled on the device.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if tags are enabled on the device, <code>false</code> otherwise.
     * @deprecated Marked to be removed in 7.0.0. Use {@link #getChannelTagRegistrationEnabled()} instead.
     */
    @Deprecated
    public boolean getDeviceTagsEnabled() {
        return getChannelTagRegistrationEnabled();
    }

    /**
     * Sets whether tags are enabled on the device. The default value is <code>true</code>.
     * If <code>false</code>, no locally specified tags will be sent to the server during registration.
     *
     * @param enabled A boolean indicating whether tags are enabled on the device.
     * @deprecated Marked to be removed in 7.0.0. Use {@link #setChannelTagRegistrationEnabled(boolean)} instead.
     */
    @Deprecated
    public void setDeviceTagsEnabled(boolean enabled) {
        setChannelTagRegistrationEnabled(enabled);
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
     * Determines whether the GCM token or ADM ID is stored during channel registration.
     * If <code>false</code>, the app will not be able to receive push notifications.
     * The default value is <code>true</code>.
     *
     * @return <code>true</code> if the GCM token or ADM ID is stored during channel registration,
     * <code>false</code> otherwise.
     */
    public boolean getPushTokenRegistrationEnabled() {
        return preferences.getPushTokenRegistrationEnabled();
    }

    /**
     * Sets whether the GCM token or ADM ID is stored during channel registration.
     * If <code>false</code>, the app will not be able to receive push notifications.
     * @param enabled A boolean indicating whether the GCM token or ADM ID is stored during
     * channel registration.
     */
    public void setPushTokenRegistrationEnabled(boolean enabled) {
        preferences.setPushTokenRegistrationEnabled(enabled);
        updateRegistration();
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
     * Determines whether channel creation is initially disabled, to be enabled later
     * by enableChannelCreation.
     *
     * @return <code>true</code> if channel creation is initially disabled, <code>false</code> otherwise.
     */
    boolean isChannelCreationDelayEnabled() {
        return channelCreationDelayEnabled;
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
    @Nullable
    public String getLastReceivedSendId() {
        return preferences.getLastReceivedSendId();
    }

    /**
     * Store the send ID from the last received push.
     *
     * @param sendId The send ID string.
     */
    void setLastReceivedSendId(String sendId) {
        preferences.setLastReceivedSendId(sendId);
    }

    /**
     * Sets the Quiet Time interval.
     *
     * @param startTime A Date instance indicating when Quiet Time should start.
     * @param endTime A Date instance indicating when Quiet Time should end.
     */
    public void setQuietTimeInterval(@NonNull Date startTime, @NonNull Date endTime) {
        preferences.setQuietTimeInterval(startTime, endTime);
    }

    /**
     * Edit the channel tag groups.
     *
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS) {
            @Override
            public TagGroupsEditor addTag(@NonNull String tagGroup, @NonNull String tag) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to add tag " + tag + " to device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }
                return super.addTag(tagGroup, tag);
            }

            @Override
            public TagGroupsEditor addTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to add tags { " + tags + " } to device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }

                return super.addTags(tagGroup, tags);
            }

            @Override
            public TagGroupsEditor removeTag(@NonNull String tagGroup, @NonNull String tag) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to remove tag " + tag + " from device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }
                return super.removeTag(tagGroup, tag);
            }

            @Override
            public TagGroupsEditor removeTags(@NonNull String tagGroup, @NonNull Set<String> tags) {
                if (channelTagRegistrationEnabled && DEFAULT_TAG_GROUP.equals(tagGroup)) {
                    Logger.error("Unable to remove tags { " + tags + " } from device tag group when channelTagRegistrationEnabled is true.");
                    return this;
                }

                return super.removeTags(tagGroup, tags);
            }
        };
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
    public void addNotificationActionButtonGroup(@NonNull String id, @NonNull NotificationActionButtonGroup group) {
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
    public void removeNotificationActionButtonGroup(@NonNull String id) {
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

    /**
     * Get the Channel ID
     *
     * @return A Channel ID string
     */
    @Nullable
    public String getChannelId() {
        return preferences.getChannelId();
    }

    /**
     * Gets the channel location.
     *
     * @return The channel location.
     */
    @Nullable
    String getChannelLocation() {
        return preferences.getChannelLocation();
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
    }

    /**
     * Sets the registered GCM ID.
     *
     * @param gcmId A GCM identifier string.
     */
    void setGcmId(String gcmId) {
        preferences.setGcmId(gcmId);
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
     * Gets the Android secure ID.
     *
     * @param context The application context.
     * @return The Android secure ID.
     * @hide
     */
    static String getSecureId(@NonNull Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Starts the push service to update tag groups.
     */
    void startUpdateTagsService() {
        Intent tagUpdateIntent = new Intent(UAirship.getApplicationContext(), PushService.class)
                .setAction(PushService.ACTION_UPDATE_CHANNEL_TAG_GROUPS);
        UAirship.getApplicationContext().startService(tagUpdateIntent);
    }

    /**
     * Sets the GCM Instance ID token.
     *
     * @param token The Instance ID token.
     */
    void setGcmToken(String token) {
        preferences.setGcmToken(token);
        preferences.setAppVersionCode(UAirship.getPackageInfo().versionCode);
        preferences.setDeviceId(getSecureId(UAirship.getApplicationContext()));
    }

    /**
     * Gets the GCM Instance ID token.
     *
     * @return The GCM token.
     */
    @Nullable
    public String getGcmToken() {
        return preferences.getGcmToken();
    }
}
