/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

/**
 * Compatibility class for supporting NotificationChannel functionality across Android OS versions.
 */
public class NotificationChannelCompat implements JsonSerializable {

    static final String CAN_BYPASS_DND_KEY = "canBypassDnd";
    static final String CAN_SHOW_BADGE_KEY = "canShowBadge";
    static final String SHOULD_SHOW_LIGHTS_KEY = "shouldShowLights";
    static final String SHOULD_VIBRATE_KEY = "shouldVibrate";
    static final String DESCRIPTION_KEY = "description";
    static final String GROUP_KEY = "group";
    static final String ID_KEY = "id";
    static final String IMPORTANCE_KEY = "importance";
    static final String LIGHT_COLOR_KEY = "lightColor";
    static final String LOCKSCREEN_VISIBILITY_KEY = "lockscreenVisibility";
    static final String NAME_KEY = "name";
    static final String SOUND_KEY = "sound";
    static final String VIBRATION_PATTERN_KEY = "vibrationPattern";
    static final String NAME_RESOURCE_KEY = "nameResource";
    static final String DESCRIPTION_RESOURCE_KEY = "descriptionResource";

    private Impl impl;
    private String nameResource = null;
    private String descriptionResource = null;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotificationChannelCompat that = (NotificationChannelCompat) o;

        return that.toJsonMap().equals(this.toJsonMap());
    }

    @Override
    public int hashCode() {
        return this.toJsonMap().hashCode();
    }

    interface Impl {
        NotificationChannel toNotificationChannel();
        boolean canBypassDnd();
        void setBypassDnd(boolean bypassDnd);
        boolean canShowBadge();
        void setShowBadge(boolean showBadge);
        boolean shouldShowLights();
        void enableLights(boolean lights);
        boolean shouldVibrate();
        void enableVibration(boolean vibration);
        String getDescription();
        void setDescription(String description);
        String getGroup();
        void setGroup(String group);
        String getId();
        int getImportance();
        void setImportance(int importance);
        int getLightColor();
        void setLightColor(int argb);
        int getLockscreenVisibility();
        void setLockscreenVisibility(int lockscreenVisibility);
        CharSequence getName();
        void setName(CharSequence name);
        Uri getSound();
        void setSound(Uri sound);
        long[] getVibrationPattern();
        void setVibrationPattern(long[] vibrationPattern);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    static class ImplOreo implements Impl {

        private NotificationChannel channel;

        public ImplOreo(String id, CharSequence name, int importance) {
            this.channel = new NotificationChannel(id, name, importance);
        }

        public ImplOreo(NotificationChannel notificationChannel) {
            this.channel = notificationChannel;
        }

        @Override
        @RequiresApi(api = Build.VERSION_CODES.O)
        public NotificationChannel toNotificationChannel() {
            return channel;
        }

        @Override
        public boolean canBypassDnd() {
            return channel.canBypassDnd();
        }

        @Override
        public void setBypassDnd(boolean bypassDnd) {
            channel.setBypassDnd(bypassDnd);
        }

        @Override
        public boolean canShowBadge() {
            return channel.canShowBadge();
        }

        @Override
        public void setShowBadge(boolean showBadge) {
            channel.setShowBadge(showBadge);
        }

        @Override
        public boolean shouldShowLights() {
            return channel.shouldShowLights();
        }

        @Override
        public void enableLights(boolean lights) {
            channel.enableLights(lights);
        }

        @Override
        public boolean shouldVibrate() {
            return channel.shouldVibrate();
        }

        @Override
        public void enableVibration(boolean vibration) {
            channel.enableVibration(vibration);
        }

        @Override
        public String getDescription() {
            return channel.getDescription();
        }

        @Override
        public void setDescription(String description) {
            channel.setDescription(description);
        }

        @Override
        public String getGroup() {
            return channel.getGroup();
        }

        @Override
        public void setGroup(String group) {
            channel.setGroup(group);
        }

        @Override
        public String getId() {
            return channel.getId();
        }

        @Override
        public int getImportance() {
            return channel.getImportance();
        }

        @Override
        public void setImportance(int importance) {
            channel.setImportance(importance);
        }

        @Override
        public int getLightColor() {
            return channel.getLightColor();
        }

        @Override
        public void setLightColor(int argb) {
            channel.setLightColor(argb);
        }

        @Override
        public int getLockscreenVisibility() {
            return channel.getLockscreenVisibility();
        }

        @Override
        public void setLockscreenVisibility(int lockscreenVisibility) {
            channel.setLockscreenVisibility(lockscreenVisibility);
        }

        @Override
        public CharSequence getName() {
            return channel.getName();
        }

        @Override
        public void setName(CharSequence name) {
            channel.setName(name);
        }

        @Override
        public Uri getSound() {
            return channel.getSound();
        }

        @Override
        public void setSound(Uri sound) {
            channel.setSound(sound, null);
        }

        @Override
        public long[] getVibrationPattern() {
            return channel.getVibrationPattern();
        }

        @Override
        public void setVibrationPattern(long[] vibrationPattern) {
            channel.setVibrationPattern(vibrationPattern);
        }
    }

    static class ImplBase implements Impl {

        boolean bypassDnd = false;
        boolean showBadge = true;
        boolean showLights = false;
        boolean shouldVibrate = false;

        String description = null;
        String group = null;

        String identifier;
        CharSequence name;
        Uri sound;

        int importance;
        int lightcolor = 0;
        int lockscreenvisibility = Notification.VISIBILITY_PUBLIC;

        long[] vibrationPattern = null;

        public ImplBase(String id, CharSequence name, int importance) {
            this.identifier = id;
            this.name = name;
            this.importance = importance;
            this.sound = Settings.System.DEFAULT_NOTIFICATION_URI;
        }

        @Override
        @RequiresApi(api = Build.VERSION_CODES.O)
        public NotificationChannel toNotificationChannel() {
            NotificationChannel channel = new NotificationChannel(identifier, name, importance);

            channel.setBypassDnd(bypassDnd);
            channel.setShowBadge(showBadge);
            channel.enableLights(showLights);
            channel.enableVibration(shouldVibrate);
            channel.setSound(sound, null);
            channel.setDescription(description);
            channel.setGroup(group);
            channel.setLightColor(lightcolor);
            channel.setLockscreenVisibility(lockscreenvisibility);
            channel.setVibrationPattern(vibrationPattern);

            return channel;
        }

        @Override
        public boolean canBypassDnd() {
            return bypassDnd;
        }

        @Override
        public void setBypassDnd(boolean bypassDnd) {
            this.bypassDnd = bypassDnd;
        }

        @Override
        public boolean canShowBadge() {
            return showBadge;
        }

        @Override
        public void setShowBadge(boolean showBadge) {
            this.showBadge = showBadge;
        }

        @Override
        public boolean shouldShowLights() {
            return showLights;
        }

        @Override
        public void enableLights(boolean lights) {
            this.showLights = lights;
        }

        @Override
        public boolean shouldVibrate() {
            return shouldVibrate;
        }

        @Override
        public void enableVibration(boolean vibration) {
            shouldVibrate = vibration;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public void setDescription(String description) {
            this.description = description;
        }

        @Override
        public String getGroup() {
            return group;
        }

        @Override
        public void setGroup(String group) {
            this.group = group;
        }

        @Override
        public String getId() {
            return identifier;
        }

        @Override
        public int getImportance() {
            return importance;
        }

        @Override
        public void setImportance(int importance) {
            this.importance = importance;
        }

        @Override
        public int getLightColor() {
            return lightcolor;
        }

        @Override
        public void setLightColor(int argb) {
            this.lightcolor = argb;
        }

        @Override
        public int getLockscreenVisibility() {
            return lockscreenvisibility;
        }

        @Override
        public void setLockscreenVisibility(int lockscreenVisibility) {
            this.lockscreenvisibility = lockscreenVisibility;
        }

        @Override
        public CharSequence getName() {
            return name;
        }

        @Override
        public void setName(CharSequence name) {
            this.name = name;
        }

        @Override
        public Uri getSound() {
            return sound;
        }

        @Override
        public void setSound(Uri sound) {
            this.sound = sound;
        }

        @Override
        public long[] getVibrationPattern() {
            return vibrationPattern;
        }

        @Override
        public void setVibrationPattern(long[] vibrationPattern) {
            this.vibrationPattern = vibrationPattern;
        }
    }

    /**
     * Factory method for creating a NotificationChannelCompat out of a JSON payload.
     *
     * @param jsonValue The JSON payload.
     * @return A NotificationChannelCompat instance, or <code>null</code> if one could not be deserialized.
     */
    public static @Nullable NotificationChannelCompat fromJson(@NonNull JsonValue jsonValue) {
        JsonMap map = jsonValue.getMap();

        if (map != null) {
            String id = map.opt(ID_KEY).getString();
            String name = map.opt(NAME_KEY).getString();
            String nameResource = map.opt(NAME_RESOURCE_KEY).getString();
            String descriptionResource = map.opt(DESCRIPTION_RESOURCE_KEY).getString();

            int importance = map.opt(IMPORTANCE_KEY).getInt(-1);

            if (id != null && name != null && importance != -1) {

                Context context = UAirship.getApplicationContext();

                NotificationChannelCompat channelCompat = new NotificationChannelCompat(id, name, importance);

                if (!UAStringUtil.isEmpty(nameResource)) {
                    channelCompat.setNameResource(nameResource, context);
                }

                if (!UAStringUtil.isEmpty(descriptionResource)) {
                    channelCompat.setDescriptionResource(descriptionResource, context);
                }

                channelCompat.setBypassDnd(map.opt(CAN_BYPASS_DND_KEY).getBoolean(false));
                channelCompat.setShowBadge(map.opt(CAN_SHOW_BADGE_KEY).getBoolean(true));
                channelCompat.enableLights(map.opt(SHOULD_SHOW_LIGHTS_KEY).getBoolean(false));
                channelCompat.enableVibration(map.opt(SHOULD_VIBRATE_KEY).getBoolean(false));
                channelCompat.setDescription(map.opt(DESCRIPTION_KEY).getString());
                channelCompat.setGroup(map.opt(GROUP_KEY).getString());
                channelCompat.setLightColor(map.opt(LIGHT_COLOR_KEY).getInt(0));
                channelCompat.setLockscreenVisibility(map.opt(LOCKSCREEN_VISIBILITY_KEY).getInt(-1000));
                channelCompat.setName(map.opt(NAME_KEY).getString());

                channelCompat.setSound(Uri.parse(map.opt(SOUND_KEY).getString()));

                JsonList vibrationPatternList = map.opt(VIBRATION_PATTERN_KEY).getList();

                if (vibrationPatternList != null) {
                    long[] vibrationPattern = new long[vibrationPatternList.size()];

                    for (int i = 0; i < vibrationPatternList.size(); i++) {
                        vibrationPattern[i] = vibrationPatternList.get(i).getLong(0);
                    }

                    channelCompat.setVibrationPattern(vibrationPattern);
                }

                return channelCompat;
            }
        }

        Logger.error("Unable to deserialize notification channel: %s", jsonValue);

        return null;
    }

    /**
     * NotificationChannelCompat constructor.
     *
     * @param notificationChannel A NotificationChannel instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public NotificationChannelCompat(NotificationChannel notificationChannel) {
        this.impl = new ImplOreo(notificationChannel);
    }

    /**
     * NotificationChannelCompat constructor.
     *
     * @param id The channel identifier.
     * @param name The channel name.
     * @param importance The notification importance.
     */
    public NotificationChannelCompat(@NonNull String id, @NonNull CharSequence name, int importance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.impl = new ImplOreo(id, name, importance);
        } else {
            this.impl = new ImplBase(id, name, importance);
        }
    }

    /**
     * Creates a corresponding NotificationChannel object for Android O and above.
     *
     * @return A NotificationChannel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public NotificationChannel toNotificationChannel() {
        return impl.toNotificationChannel();
    }

    /**
     * Indicates whether the channel can bypass do-not-disturb.
     *
     * @return <code>true</code> if the channel can bypass do-not-distrub, <code>false</code> otherwise.
     */
    public boolean canBypassDnd() {
        return impl.canBypassDnd();
    }

    /**
     * Sets whether the channel can bypass do-not-disturb.
     *
     * @param bypassDnd Whether the channel can bypass do-not-disturb.
     */
    public void setBypassDnd(boolean bypassDnd) {
        impl.setBypassDnd(bypassDnd);
    }

    /**
     * Indicates whether the channel can show badges
     *
     * @return <code>true</code> if the channel can show badges, <code>false</code> otherwise.
     */
    public boolean canShowBadge() {
        return impl.canShowBadge();
    }

    /**
     * Sets whether the channel can show badges.
     *
     * @param showBadge Whether the channel can show badges.
     */
    public void setShowBadge(boolean showBadge) {
        impl.setShowBadge(showBadge);
    }

    /**
     * Indicates whether the channel can show lights.
     *
     * @return <code>true</code> if the channel can show lights, <code>false</code> otherwise.
     */
    public boolean shouldShowLights() {
        return impl.shouldShowLights();
    }

    /**
     * Sets whether the channel can show lights.
     *
     * @param lights Whether the channel can show lights.
     */
    public void enableLights(boolean lights) {
        impl.enableLights(lights);
    }

    /**
     * Indicates whether the channel can vibrate.
     *
     * @return <code>true</code> if the channel can vibrate, <code>false</code> otherwise.
     */
    public boolean shouldVibrate() {
        return impl.shouldVibrate();
    }

    /**
     * Sets whether the channel can vibrate.
     *
     * @param vibration Whether the channel can vibrate.
     */
    public void enableVibration(boolean vibration) {
        impl.enableVibration(vibration);
    }

    /**
     * Gets the channel's description.
     *
     * @return The description.
     */
    public String getDescription() {
        return impl.getDescription();
    }

    /**
     * Sets the channel's description.
     *
     * @param description The description.
     */
    public void setDescription(String description) {
        impl.setDescription(description);
    }

    /**
     * Gets the channel's group.
     *
     * @return The group.
     */
    public String getGroup() {
        return impl.getGroup();
    }

    /**
     * Sets the channel's group.
     *
     * @param group The group.
     */
    public void setGroup(String group) {
        impl.setGroup(group);
    }

    /**
     * Gets the channel's identifier.
     *
     * @return The identifier.
     */
    public String getId() {
        return impl.getId();
    }

    /**
     * Gets the channel's importance.
     *
     * @return The importance.
     */
    public int getImportance() {
        return impl.getImportance();
    }

    /**
     * Sets the channel's importance.
     *
     * @param importance The importance.
     */
    public void setImportance(int importance) {
        impl.setImportance(importance);
    }

    /**
     * Gets the channel's light color.
     *
     * @return The light color.
     */
    public int getLightColor() {
        return impl.getLightColor();
    }

    /**
     * Sets the channel's light color.
     *
     * @param argb The light color.
     */
    public void setLightColor(int argb) {
        impl.setLightColor(argb);
    }

    /**
     * Gets the channel's lockscreen visibility.
     *
     * @return The lockscreen visibility.
     */
    public int getLockscreenVisibility() {
        return impl.getLockscreenVisibility();
    }

    /**
     * Sets the channel's lockscreen visibility.
     *
     * @param lockscreenVisibility The lockscreen visibility.
     */
    public void setLockscreenVisibility(int lockscreenVisibility) {
        impl.setLockscreenVisibility(lockscreenVisibility);
    }

    /**
     * Gets the channel's name.
     *
     * @return The name.
     */
    public CharSequence getName() {
        return impl.getName();
    }

    /**
     * Sets the channel's name.
     *
     * @param name The name.
     */
    public void setName(CharSequence name) {
        impl.setName(name);
    }

    /**
     * Gets the channel's sound.
     *
     * @return The sound.
     */
    public Uri getSound() {
        return impl.getSound();
    }

    /**
     * Sets the channel's sound.
     *
     * @param sound The sound.
     */
    public void setSound(Uri sound) {
        impl.setSound(sound);
    }

    /**
     * Gets the channel's vibration pattern.
     *
     * @return The vibration pattern.
     */
    public long[] getVibrationPattern() {
        return impl.getVibrationPattern();
    }

    /**
     * Sets the channel's vibration pattern.
     *
     * @param vibrationPattern The vibration pattern.
     */
    public void setVibrationPattern(long[] vibrationPattern) {
        impl.setVibrationPattern(vibrationPattern);
    }

    /**
     * Gets the channel's name string resource.
     *
     * @return The name resource, or <code>null</code> if one has not been set.
     */
    @Nullable
    public String getNameResource() {
        return nameResource;
    }

    /**
     * Sets the channel's name string resource. If available, this will be used to set a
     * localized channel name.
     *
     * @param nameResource The name resource.
     * @param context The application context.
     */
    public void setNameResource(@NonNull String nameResource, Context context) {
        this.nameResource = nameResource;
        setName(UAStringUtil.namedStringResource(context, nameResource, getName().toString()));
    }

    /**
     * Gets the channel's description string resource.
     *
     * @return The description resource, or <code>null</code> if one has not been set.
     */
    @Nullable
    public String getDescriptionResource() {
        return descriptionResource;
    }

    /**
     * Sets the channel's description string resource. If available, this will be used to
     * set a localized channel description.
     *
     * @param descriptionResource The description resource.
     * @param context The application context.
     */
    public void setDescriptionResource(@NonNull String descriptionResource, Context context) {
        this.descriptionResource = descriptionResource;
        setDescription(UAStringUtil.namedStringResource(context, descriptionResource, getDescription()));
    }

    /**
     * Converts the channel to a JSON object for serialization.
     *
     * @return A JsonMap.
     */
    public JsonMap toJsonMap() {
        return JsonMap.newBuilder()
                      .put(CAN_BYPASS_DND_KEY, canBypassDnd())
                      .put(CAN_SHOW_BADGE_KEY, canShowBadge())
                      .put(SHOULD_SHOW_LIGHTS_KEY, shouldShowLights())
                      .put(SHOULD_VIBRATE_KEY, shouldVibrate())
                      .putOpt(DESCRIPTION_KEY, getDescription())
                      .putOpt(GROUP_KEY, getGroup())
                      .put(ID_KEY, getId())
                      .put(IMPORTANCE_KEY, getImportance())
                      .put(LIGHT_COLOR_KEY, getLightColor())
                      .put(LOCKSCREEN_VISIBILITY_KEY, getLockscreenVisibility())
                      .put(NAME_KEY, getName().toString())
                      .putOpt(SOUND_KEY, getSound() != null ? getSound().toString() : null)
                      .put(VIBRATION_PATTERN_KEY, JsonValue.wrapOpt(getVibrationPattern()))
                      .putOpt(NAME_RESOURCE_KEY, getNameResource())
                      .putOpt(DESCRIPTION_RESOURCE_KEY, getDescriptionResource())
                      .build();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return toJsonMap().toJsonValue();
    }
}
