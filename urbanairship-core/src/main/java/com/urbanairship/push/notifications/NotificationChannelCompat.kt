/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.content.ContentResolver
import android.content.Context
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Xml
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.XmlRes
import androidx.core.util.ObjectsCompat
import com.urbanairship.UALog
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.AttributeSetConfigParser
import java.io.IOException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Compatibility class for supporting [NotificationChannel] functionality across Android OS versions.
 */
public class NotificationChannelCompat : JsonSerializable {

    /**
     * Indicates whether the channel can bypass do-not-disturb.
     */
    @JvmField
    public var bypassDnd: Boolean = false

    /**
     * Indicates whether the channel can show badges
     */
    public var showBadge: Boolean = true

    private var showLights = false
    private var shouldVibrate = false

    /**
     * The channel's description.
     */
    @JvmField
    public var description: String? = null

    /**
     * The channel's group.
     */
    @JvmField
    public var group: String? = null

    /**
     * The channel's identifier.
     */
    public val id: String

    /**
     * The channel's name.
     */
    public var name: CharSequence

    /**
     * The channel's sound.
     */
    @JvmField
    public var sound: Uri? = Settings.System.DEFAULT_NOTIFICATION_URI

    /**
     * The channel's importance.
     */
    @JvmField
    public var importance: Int

    /**
     * The channel's light color.
     */
    @JvmField
    public var lightColor: Int = 0

    /**
     * The channel's lockscreen visibility.
     */
    @JvmField
    public var lockscreenVisibility: Int = LOCKSCREEN_VISIBILITY_DEFAULT_VALUE

    /**
     * The channel's vibration pattern.
     */
    @JvmField
    public var vibrationPattern: LongArray? = null

    /**
     * NotificationChannelCompat constructor.
     *
     * @param notificationChannel A NotificationChannel instance.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public constructor(notificationChannel: NotificationChannel) {
        this.bypassDnd = notificationChannel.canBypassDnd()
        this.showBadge = notificationChannel.canShowBadge()
        this.showLights = notificationChannel.shouldShowLights()
        this.shouldVibrate = notificationChannel.shouldVibrate()
        this.description = notificationChannel.description
        this.group = notificationChannel.group
        this.id = notificationChannel.id
        this.name = notificationChannel.name
        this.sound = notificationChannel.sound
        this.importance = notificationChannel.importance
        this.lightColor = notificationChannel.lightColor
        this.lockscreenVisibility = notificationChannel.lockscreenVisibility
        this.vibrationPattern = notificationChannel.vibrationPattern
    }

    /**
     * NotificationChannelCompat constructor.
     *
     * @param id The channel identifier.
     * @param name The channel name.
     * @param importance The notification importance.
     */
    public constructor(id: String, name: CharSequence, importance: Int) {
        this.id = id
        this.name = name
        this.importance = importance
    }

    /**
     * Creates a corresponding NotificationChannel object for Android O and above.
     *
     * @return A NotificationChannel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public fun toNotificationChannel(): NotificationChannel {
        val channel = NotificationChannel(id, name, importance)
        channel.setBypassDnd(bypassDnd)
        channel.setShowBadge(showBadge)
        channel.enableLights(showLights)
        channel.enableVibration(shouldVibrate)
        channel.description = description
        channel.group = group
        channel.lightColor = lightColor
        channel.vibrationPattern = vibrationPattern
        channel.lockscreenVisibility = lockscreenVisibility
        channel.setSound(sound, Notification.AUDIO_ATTRIBUTES_DEFAULT)
        return channel
    }

    /**
     * Indicates whether the channel can show lights.
     *
     * @return `true` if the channel can show lights, `false` otherwise.
     */
    public fun shouldShowLights(): Boolean {
        return this.showLights
    }

    /**
     * Sets whether the channel can show lights.
     *
     * @param lights Whether the channel can show lights.
     */
    public fun enableLights(lights: Boolean) {
        this.showLights = lights
    }

    /**
     * Indicates whether the channel can vibrate.
     *
     * @return `true` if the channel can vibrate, `false` otherwise.
     */
    public fun shouldVibrate(): Boolean {
        return this.shouldVibrate
    }

    /**
     * Sets whether the channel can vibrate.
     *
     * @param vibration Whether the channel can vibrate.
     */
    public fun enableVibration(vibration: Boolean) {
        this.shouldVibrate = vibration
    }

    override fun toJsonValue(): JsonValue = jsonMapOf(
        CAN_BYPASS_DND_KEY to bypassDnd,
        CAN_SHOW_BADGE_KEY to showBadge,
        SHOULD_SHOW_LIGHTS_KEY to shouldShowLights(),
        SHOULD_VIBRATE_KEY to shouldVibrate(),
        DESCRIPTION_KEY to description,
        GROUP_KEY to group,
        ID_KEY to id,
        IMPORTANCE_KEY to importance,
        LIGHT_COLOR_KEY to lightColor,
        LOCKSCREEN_VISIBILITY_KEY to lockscreenVisibility,
        NAME_KEY to name.toString(),
        SOUND_KEY to sound?.toString(),
        VIBRATION_PATTERN_KEY to JsonValue.wrap(vibrationPattern)
    ).toJsonValue()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false

        val that = other as NotificationChannelCompat

        if (bypassDnd != that.bypassDnd) return false
        if (showBadge != that.showBadge) return false
        if (showLights != that.showLights) return false
        if (shouldVibrate != that.shouldVibrate) return false
        if (importance != that.importance) return false
        if (lightColor != that.lightColor) return false
        if (lockscreenVisibility != that.lockscreenVisibility) return false
        if (description != that.description) return false
        if (group != that.group) return false
        if (id != that.id) return false
        if (name != that.name) return false
        if (sound != that.sound) return false
        if (!vibrationPattern.contentEquals(that.vibrationPattern)) return false

        return true
    }

    override fun hashCode(): Int {
        return ObjectsCompat.hash(bypassDnd, showBadge, showLights, shouldVibrate, description,
            group, id, name, sound, importance, lightColor, lockscreenVisibility, vibrationPattern)
    }

    override fun toString(): String {
        return "NotificationChannelCompat{bypassDnd=$bypassDnd, showBadge=$showBadge, " +
                "showLights=$showLights, shouldVibrate=$shouldVibrate, description='$description', " +
                "group='$group', identifier='$id', name=$name, sound=$sound, importance=$importance, " +
                "lightColor=$lightColor, lockscreenVisibility=$lockscreenVisibility, " +
                "vibrationPattern=${vibrationPattern.contentToString()}}"
    }

    public companion object {

        private const val LOCKSCREEN_VISIBILITY_DEFAULT_VALUE = -1000
        private const val NOTIFICATION_CHANNEL_TAG = "NotificationChannel"

        private const val CAN_BYPASS_DND_KEY = "can_bypass_dnd"
        private const val CAN_SHOW_BADGE_KEY = "can_show_badge"
        private const val SHOULD_SHOW_LIGHTS_KEY = "should_show_lights"
        private const val SHOULD_VIBRATE_KEY = "should_vibrate"
        private const val DESCRIPTION_KEY = "description"
        private const val GROUP_KEY = "group"
        private const val ID_KEY = "id"
        private const val IMPORTANCE_KEY = "importance"
        private const val LIGHT_COLOR_KEY = "light_color"
        private const val LOCKSCREEN_VISIBILITY_KEY = "lockscreen_visibility"
        private const val NAME_KEY = "name"
        private const val SOUND_KEY = "sound"
        private const val VIBRATION_PATTERN_KEY = "vibration_pattern"

        /**
         * Factory method for creating a NotificationChannelCompat out of a JSON payload.
         *
         * @param jsonValue The JSON payload.
         * @return A [NotificationChannelCompat] instance, or `null` if one could not be deserialized.
         */
        @JvmStatic
        public fun fromJson(jsonValue: JsonValue): NotificationChannelCompat? {
            val failed: () -> NotificationChannelCompat? = {
                UALog.e("Unable to deserialize notification channel: $jsonValue")
                null
            }

            val map = jsonValue.map ?: return failed()

            val id = map.opt(ID_KEY).string ?: return failed()
            val name = map.opt(NAME_KEY).string ?: return failed()

            val importance = map.opt(IMPORTANCE_KEY).getInt(-1)
            if (importance == -1) return failed()

            val channelCompat = NotificationChannelCompat(id, name, importance)
            channelCompat.bypassDnd = map.opt(CAN_BYPASS_DND_KEY).getBoolean(false)
            channelCompat.showBadge = map.opt(CAN_SHOW_BADGE_KEY).getBoolean(true)
            channelCompat.enableLights(map.opt(SHOULD_SHOW_LIGHTS_KEY).getBoolean(false))
            channelCompat.enableVibration(map.opt(SHOULD_VIBRATE_KEY).getBoolean(false))
            channelCompat.description = map.opt(DESCRIPTION_KEY).string
            channelCompat.group = map.opt(GROUP_KEY).string
            channelCompat.lightColor = map.opt(LIGHT_COLOR_KEY).getInt(0)
            channelCompat.lockscreenVisibility = map.opt(LOCKSCREEN_VISIBILITY_KEY)
                .getInt(LOCKSCREEN_VISIBILITY_DEFAULT_VALUE)
            channelCompat.name = map.opt(NAME_KEY).optString()

            map.opt(SOUND_KEY).string?.let {
                if (it.isNotEmpty()) {
                    channelCompat.sound = Uri.parse(it)
                }
            }

            map.opt(VIBRATION_PATTERN_KEY)
                .list
                ?.map { it.getLong(0) }
                ?.let { channelCompat.vibrationPattern = it.toLongArray() }

            return channelCompat
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
        public fun fromXml(
            context: Context,
            @XmlRes resource: Int
        ): List<NotificationChannelCompat> {
            val parser = context.resources.getXml(resource)
            try {
                return parseChannels(context, parser)
            } catch (e: Exception) {
                UALog.e(e, "Failed to parse channels")
            } finally {
                parser.close()
            }

            return emptyList()
        }

        @Throws(IOException::class, XmlPullParserException::class)
        private fun parseChannels(
            context: Context,
            parser: XmlResourceParser
        ): List<NotificationChannelCompat> {
            val channels = mutableListOf<NotificationChannelCompat>()

            while (XmlPullParser.END_DOCUMENT != parser.next()) {
                // Start component
                if (XmlPullParser.START_TAG == parser.eventType && NOTIFICATION_CHANNEL_TAG == parser.name) {
                    val configParser = AttributeSetConfigParser(context, Xml.asAttributeSet(parser))

                    val name = configParser.getString(NAME_KEY)
                    val id = configParser.getString(ID_KEY)
                    val importance = configParser.getInt(IMPORTANCE_KEY, -1)

                    if (name.isNullOrEmpty() || id.isNullOrEmpty() || importance == -1) {
                        UALog.e(
                            "Invalid notification channel. Missing name ($name), id ($id), or importance ($importance)"
                        )
                        continue
                    }

                    val channelCompat = NotificationChannelCompat(id, name, importance)
                    channelCompat.bypassDnd = configParser.getBoolean(CAN_BYPASS_DND_KEY, false)
                    channelCompat.showBadge = configParser.getBoolean(CAN_SHOW_BADGE_KEY, true)
                    channelCompat.enableLights(
                        configParser.getBoolean(SHOULD_SHOW_LIGHTS_KEY, false)
                    )
                    channelCompat.enableVibration(
                        configParser.getBoolean(SHOULD_VIBRATE_KEY, false)
                    )
                    channelCompat.description = configParser.getString(DESCRIPTION_KEY)
                    channelCompat.group = configParser.getString(GROUP_KEY)
                    channelCompat.lightColor = configParser.getColor(LIGHT_COLOR_KEY, 0)
                    channelCompat.lockscreenVisibility = configParser.getInt(
                        LOCKSCREEN_VISIBILITY_KEY, LOCKSCREEN_VISIBILITY_DEFAULT_VALUE
                    )

                    val soundResource = configParser.getRawResourceId(SOUND_KEY)
                    if (soundResource != 0) {
                        val uri = Uri.parse(
                            ContentResolver.SCHEME_ANDROID_RESOURCE +
                                    "://" + context.packageName + "/raw/"
                                    + context.resources.getResourceEntryName(soundResource)
                        )
                        channelCompat.sound = uri
                    } else {
                        val sound = configParser.getString(SOUND_KEY)
                        if (!sound.isNullOrEmpty()) {
                            channelCompat.sound = Uri.parse(sound)
                        }
                    }

                    val vibrationPatternString = configParser.getString(VIBRATION_PATTERN_KEY)
                    if (!vibrationPatternString.isNullOrEmpty()) {
                        val vibration = vibrationPatternString
                            .split(",".toRegex())
                            .dropLastWhile { it.isEmpty() }
                            .map { it.toLong() }
                            .toLongArray()

                        channelCompat.vibrationPattern = vibration
                    }

                    channels.add(channelCompat)
                }
            }

            return channels
        }
    }
}
