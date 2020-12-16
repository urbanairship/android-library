/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.app.NotificationChannel;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Xml;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.AttributeSetConfigParser;
import com.urbanairship.util.UAStringUtil;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.XmlRes;

/**
 * Compatibility class for supporting NotificationChannel functionality across Android OS versions.
 */
public class NotificationChannelCompat implements JsonSerializable {

    private static final int LOCKSCREEN_VISIBILITY_DEFAULT_VALUE = -1000;
    private static final String NOTIFICATION_CHANNEL_TAG = "NotificationChannel";

    private static final String CAN_BYPASS_DND_KEY = "can_bypass_dnd";
    private static final String CAN_SHOW_BADGE_KEY = "can_show_badge";
    private static final String SHOULD_SHOW_LIGHTS_KEY = "should_show_lights";
    private static final String SHOULD_VIBRATE_KEY = "should_vibrate";
    private static final String DESCRIPTION_KEY = "description";
    private static final String GROUP_KEY = "group";
    private static final String ID_KEY = "id";
    private static final String IMPORTANCE_KEY = "importance";
    private static final String LIGHT_COLOR_KEY = "light_color";
    private static final String LOCKSCREEN_VISIBILITY_KEY = "lockscreen_visibility";
    private static final String NAME_KEY = "name";
    private static final String SOUND_KEY = "sound";
    private static final String VIBRATION_PATTERN_KEY = "vibration_pattern";

    private boolean bypassDnd = false;
    private boolean showBadge = true;
    private boolean showLights = false;
    private boolean shouldVibrate = false;

    private String description = null;
    private String group = null;

    private final String identifier;
    private CharSequence name;
    private Uri sound = Settings.System.DEFAULT_NOTIFICATION_URI;

    private int importance;
    private int lightColor = 0;
    private int lockscreenVisibility = LOCKSCREEN_VISIBILITY_DEFAULT_VALUE;
    private long[] vibrationPattern = null;

    /**
     * NotificationChannelCompat constructor.
     *
     * @param notificationChannel A NotificationChannel instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public NotificationChannelCompat(@NonNull NotificationChannel notificationChannel) {
        this.bypassDnd = notificationChannel.canBypassDnd();
        this.showBadge = notificationChannel.canShowBadge();
        this.showLights = notificationChannel.shouldShowLights();
        this.shouldVibrate = notificationChannel.shouldVibrate();
        this.description = notificationChannel.getDescription();
        this.group = notificationChannel.getGroup();
        this.identifier = notificationChannel.getId();
        this.name = notificationChannel.getName();
        this.sound = notificationChannel.getSound();
        this.importance = notificationChannel.getImportance();
        this.lightColor = notificationChannel.getLightColor();
        this.lockscreenVisibility = notificationChannel.getLockscreenVisibility();
        this.vibrationPattern = notificationChannel.getVibrationPattern();
    }

    /**
     * NotificationChannelCompat constructor.
     *
     * @param id The channel identifier.
     * @param name The channel name.
     * @param importance The notification importance.
     */
    public NotificationChannelCompat(@NonNull String id, @NonNull CharSequence name, int importance) {
        this.identifier = id;
        this.name = name;
        this.importance = importance;
    }

    /**
     * Creates a corresponding NotificationChannel object for Android O and above.
     *
     * @return A NotificationChannel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @NonNull
    public NotificationChannel toNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(identifier, name, importance);
        channel.setBypassDnd(bypassDnd);
        channel.setShowBadge(showBadge);
        channel.enableLights(showLights);
        channel.enableVibration(shouldVibrate);
        channel.setDescription(description);
        channel.setGroup(group);
        channel.setLightColor(lightColor);
        channel.setVibrationPattern(vibrationPattern);
        channel.setLockscreenVisibility(lockscreenVisibility);
        channel.setSound(sound, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        return channel;
    }

    /**
     * Indicates whether the channel can bypass do-not-disturb.
     *
     * @return <code>true</code> if the channel can bypass do-not-distrub, <code>false</code> otherwise.
     */
    public boolean getBypassDnd() {
        return this.bypassDnd;
    }

    /**
     * Sets whether the channel can bypass do-not-disturb.
     *
     * @param bypassDnd Whether the channel can bypass do-not-disturb.
     */
    public void setBypassDnd(boolean bypassDnd) {
        this.bypassDnd = bypassDnd;
    }

    /**
     * Indicates whether the channel can show badges
     *
     * @return <code>true</code> if the channel can show badges, <code>false</code> otherwise.
     */
    public boolean getShowBadge() {
        return this.showBadge;
    }


    /**
     * Sets whether the channel can show badges.
     *
     * @param showBadge Whether the channel can show badges.
     */
    public void setShowBadge(boolean showBadge) {
        this.showBadge = showBadge;
    }

    /**
     * Indicates whether the channel can show lights.
     *
     * @return <code>true</code> if the channel can show lights, <code>false</code> otherwise.
     */
    public boolean shouldShowLights() {
        return this.showLights;
    }

    /**
     * Sets whether the channel can show lights.
     *
     * @param lights Whether the channel can show lights.
     */
    public void enableLights(boolean lights) {
        this.showLights = lights;
    }

    /**
     * Indicates whether the channel can vibrate.
     *
     * @return <code>true</code> if the channel can vibrate, <code>false</code> otherwise.
     */
    public boolean shouldVibrate() {
        return this.shouldVibrate;
    }

    /**
     * Sets whether the channel can vibrate.
     *
     * @param vibration Whether the channel can vibrate.
     */
    public void enableVibration(boolean vibration) {
        this.shouldVibrate = vibration;
    }

    /**
     * Gets the channel's description.
     *
     * @return The description.
     */
    @Nullable
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the channel's description.
     *
     * @param description The description.
     */

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    /**
     * Gets the channel's group.
     *
     * @return The group.
     */
    @Nullable
    public String getGroup() {
        return this.group;
    }

    /**
     * Sets the channel's group.
     *
     * @param group The group.
     */
    public void setGroup(@Nullable String group) {
        this.group = group;
    }

    /**
     * Gets the channel's identifier.
     *
     * @return The identifier.
     */
    @NonNull
    public String getId() {
        return this.identifier;
    }

    /**
     * Gets the channel's importance.
     *
     * @return The importance.
     */
    public int getImportance() {
        return this.importance;
    }

    /**
     * Sets the channel's importance.
     *
     * @param importance The importance.
     */
    public void setImportance(int importance) {
        this.importance = importance;
    }

    /**
     * Gets the channel's light color.
     *
     * @return The light color.
     */
    public int getLightColor() {
        return this.lightColor;
    }

    /**
     * Sets the channel's light color.
     *
     * @param argb The light color.
     */
    public void setLightColor(int argb) {
        this.lightColor = argb;
    }

    /**
     * Gets the channel's lockscreen visibility.
     *
     * @return The lockscreen visibility.
     */
    public int getLockscreenVisibility() {
        return this.lockscreenVisibility;
    }

    /**
     * Sets the channel's lockscreen visibility.
     *
     * @param lockscreenVisibility The lockscreen visibility.
     */
    public void setLockscreenVisibility(int lockscreenVisibility) {
        this.lockscreenVisibility = lockscreenVisibility;
    }

    /**
     * Gets the channel's name.
     *
     * @return The name.
     */
    @NonNull
    public CharSequence getName() {
        return this.name;
    }

    /**
     * Sets the channel's name.
     *
     * @param name The name.
     */
    public void setName(@NonNull CharSequence name) {
        this.name = name;
    }

    /**
     * Gets the channel's sound.
     *
     * @return The sound.
     */
    @Nullable
    public Uri getSound() {
        return this.sound;
    }

    /**
     * Sets the channel's sound.
     *
     * @param sound The sound.
     */
    public void setSound(@Nullable Uri sound) {
        this.sound = sound;
    }

    /**
     * Gets the channel's vibration pattern.
     *
     * @return The vibration pattern.
     */
    @Nullable
    public long[] getVibrationPattern() {
        return this.vibrationPattern;
    }

    /**
     * Sets the channel's vibration pattern.
     *
     * @param vibrationPattern The vibration pattern.
     */
    public void setVibrationPattern(@Nullable long[] vibrationPattern) {
        this.vibrationPattern = vibrationPattern;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(CAN_BYPASS_DND_KEY, getBypassDnd())
                      .putOpt(CAN_SHOW_BADGE_KEY, getShowBadge())
                      .putOpt(SHOULD_SHOW_LIGHTS_KEY, shouldShowLights())
                      .putOpt(SHOULD_VIBRATE_KEY, shouldVibrate())
                      .putOpt(DESCRIPTION_KEY, getDescription())
                      .putOpt(GROUP_KEY, getGroup())
                      .putOpt(ID_KEY, getId())
                      .putOpt(IMPORTANCE_KEY, getImportance())
                      .putOpt(LIGHT_COLOR_KEY, getLightColor())
                      .putOpt(LOCKSCREEN_VISIBILITY_KEY, getLockscreenVisibility())
                      .putOpt(NAME_KEY, getName().toString())
                      .putOpt(SOUND_KEY, getSound() != null ? getSound().toString() : null)
                      .putOpt(VIBRATION_PATTERN_KEY, JsonValue.wrapOpt(getVibrationPattern()))
                      .build()
                      .toJsonValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NotificationChannelCompat that = (NotificationChannelCompat) o;

        if (bypassDnd != that.bypassDnd) return false;
        if (showBadge != that.showBadge) return false;
        if (showLights != that.showLights) return false;
        if (shouldVibrate != that.shouldVibrate) return false;
        if (importance != that.importance) return false;
        if (lightColor != that.lightColor) return false;
        if (lockscreenVisibility != that.lockscreenVisibility) return false;
        if (description != null ? !description.equals(that.description) : that.description != null)
            return false;
        if (group != null ? !group.equals(that.group) : that.group != null) return false;
        if (identifier != null ? !identifier.equals(that.identifier) : that.identifier != null)
            return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (sound != null ? !sound.equals(that.sound) : that.sound != null) return false;
        return Arrays.equals(vibrationPattern, that.vibrationPattern);
    }

    @Override
    public int hashCode() {
        int result = (bypassDnd ? 1 : 0);
        result = 31 * result + (showBadge ? 1 : 0);
        result = 31 * result + (showLights ? 1 : 0);
        result = 31 * result + (shouldVibrate ? 1 : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (group != null ? group.hashCode() : 0);
        result = 31 * result + (identifier != null ? identifier.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (sound != null ? sound.hashCode() : 0);
        result = 31 * result + importance;
        result = 31 * result + lightColor;
        result = 31 * result + lockscreenVisibility;
        result = 31 * result + Arrays.hashCode(vibrationPattern);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationChannelCompat{" +
                "bypassDnd=" + bypassDnd +
                ", showBadge=" + showBadge +
                ", showLights=" + showLights +
                ", shouldVibrate=" + shouldVibrate +
                ", description='" + description + '\'' +
                ", group='" + group + '\'' +
                ", identifier='" + identifier + '\'' +
                ", name=" + name +
                ", sound=" + sound +
                ", importance=" + importance +
                ", lightColor=" + lightColor +
                ", lockscreenVisibility=" + lockscreenVisibility +
                ", vibrationPattern=" + Arrays.toString(vibrationPattern) +
                '}';
    }

    /**
     * Factory method for creating a NotificationChannelCompat out of a JSON payload.
     *
     * @param jsonValue The JSON payload.
     * @return A NotificationChannelCompat instance, or <code>null</code> if one could not be deserialized.
     */
    public static @Nullable
    NotificationChannelCompat fromJson(@NonNull JsonValue jsonValue) {
        JsonMap map = jsonValue.getMap();

        if (map != null) {
            String id = map.opt(ID_KEY).getString();
            String name = map.opt(NAME_KEY).getString();

            int importance = map.opt(IMPORTANCE_KEY).getInt(-1);

            if (id != null && name != null && importance != -1) {
                NotificationChannelCompat channelCompat = new NotificationChannelCompat(id, name, importance);
                channelCompat.setBypassDnd(map.opt(CAN_BYPASS_DND_KEY).getBoolean(false));
                channelCompat.setShowBadge(map.opt(CAN_SHOW_BADGE_KEY).getBoolean(true));
                channelCompat.enableLights(map.opt(SHOULD_SHOW_LIGHTS_KEY).getBoolean(false));
                channelCompat.enableVibration(map.opt(SHOULD_VIBRATE_KEY).getBoolean(false));
                channelCompat.setDescription(map.opt(DESCRIPTION_KEY).getString());
                channelCompat.setGroup(map.opt(GROUP_KEY).getString());
                channelCompat.setLightColor(map.opt(LIGHT_COLOR_KEY).getInt(0));
                channelCompat.setLockscreenVisibility(map.opt(LOCKSCREEN_VISIBILITY_KEY).getInt(LOCKSCREEN_VISIBILITY_DEFAULT_VALUE));
                channelCompat.setName(map.opt(NAME_KEY).optString());

                String sound = map.opt(SOUND_KEY).getString();
                if (!UAStringUtil.isEmpty(sound)) {
                    channelCompat.setSound(Uri.parse(sound));
                }

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
     * Parses notification channels from an Xml file.
     *
     * @param context The context.
     * @param resource The Xml resource Id.
     * @return A list of notification channels.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public static List<NotificationChannelCompat> fromXml(@NonNull Context context, @XmlRes int resource) {
        XmlResourceParser parser = context.getResources().getXml(resource);
        try {
            return parseChannels(context, parser);
        } catch (Exception e) {
            Logger.error(e, "Failed to parse channels");
        } finally {
            parser.close();
        }

        return Collections.emptyList();
    }

    private static List<NotificationChannelCompat> parseChannels(Context context, XmlResourceParser parser) throws IOException, XmlPullParserException {
        List<NotificationChannelCompat> channels = new ArrayList<>();

        while (XmlPullParser.END_DOCUMENT != parser.next()) {
            // Start component
            if (XmlPullParser.START_TAG == parser.getEventType() && NOTIFICATION_CHANNEL_TAG.equals(parser.getName())) {
                AttributeSetConfigParser configParser = new AttributeSetConfigParser(context, Xml.asAttributeSet(parser));

                String name = configParser.getString(NAME_KEY);
                String id = configParser.getString(ID_KEY);
                int importance = configParser.getInt(IMPORTANCE_KEY, -1);

                if (UAStringUtil.isEmpty(name) || UAStringUtil.isEmpty(id) || importance == -1) {
                    Logger.error("Invalid notification channel. Missing name (%s), id (%s), or importance (%s)", name, id, importance);
                    continue;
                }

                NotificationChannelCompat channelCompat = new NotificationChannelCompat(id, name, importance);
                channelCompat.setBypassDnd(configParser.getBoolean(CAN_BYPASS_DND_KEY, false));
                channelCompat.setShowBadge(configParser.getBoolean(CAN_SHOW_BADGE_KEY, true));
                channelCompat.enableLights(configParser.getBoolean(SHOULD_SHOW_LIGHTS_KEY, false));
                channelCompat.enableVibration(configParser.getBoolean(SHOULD_VIBRATE_KEY, false));
                channelCompat.setDescription(configParser.getString(DESCRIPTION_KEY));
                channelCompat.setGroup(configParser.getString(GROUP_KEY));
                channelCompat.setLightColor(configParser.getColor(LIGHT_COLOR_KEY, 0));
                channelCompat.setLockscreenVisibility(configParser.getInt(LOCKSCREEN_VISIBILITY_KEY, LOCKSCREEN_VISIBILITY_DEFAULT_VALUE));

                int soundResource = configParser.getRawResourceId(SOUND_KEY);
                if (soundResource != 0) {
                    Uri uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" +
                            context.getPackageName() + "/raw/" + context.getResources().getResourceEntryName(soundResource));
                    channelCompat.setSound(uri);
                } else {
                    String sound = configParser.getString(SOUND_KEY);
                    if (!UAStringUtil.isEmpty(sound)) {
                        channelCompat.setSound(Uri.parse(sound));
                    }
                }

                String vibrationPatternString = configParser.getString(VIBRATION_PATTERN_KEY);
                if (!UAStringUtil.isEmpty(vibrationPatternString)) {
                    String[] stringArray = vibrationPatternString.split(",");
                    long[] vibration = new long[stringArray.length];
                    for (int i = 0; i < stringArray.length; i++) {
                        vibration[i] = Long.parseLong(stringArray[i]);
                    }

                    channelCompat.setVibrationPattern(vibration);
                }

                channels.add(channelCompat);
            }
        }

        return channels;
    }

}
