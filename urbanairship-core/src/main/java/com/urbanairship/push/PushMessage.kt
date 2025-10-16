/* Copyright Airship and Contributors */
package com.urbanairship.push

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.DrawableRes
import com.urbanairship.Airship
import com.urbanairship.UALog
import com.urbanairship.actions.ActionValue
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.util.Clock

/**
 * A push message, usually created from handling a message intent from either FCM,
 * or another push notification service
 */
public class PushMessage : Parcelable, JsonSerializable {

    private var pushBundle: Bundle? = null
    private val data: Map<String, String>
    private val clock: Clock
    private var sound: Uri? = null

    /**
     * Create a new PushMessage
     *
     * @param pushBundle The intent extras for the push
     */
    public constructor(pushBundle: Bundle) : this(pushBundle, Clock.DEFAULT_CLOCK)

    /**
     * Create a new PushMessage
     *
     * @param data The push data.
     */
    public constructor(data: Map<String, String>) {
        this.data = data.toMap()
        this.clock = Clock.DEFAULT_CLOCK
    }

    internal constructor(pushBundle: Bundle, clock: Clock) {
        this.pushBundle = pushBundle

        this.data = pushBundle.keySet()
            .mapNotNull {
                val value = pushBundle.getString(it) ?: return@mapNotNull null
                it to value
            }
            .toMap()

        this.clock = clock
    }

    /**
     * Checks if the expiration exists and is expired
     */
    public val isExpired: Boolean
        get() {
            val expirationStr = data[EXTRA_EXPIRATION] ?: return false
            if (expirationStr.isEmpty()) return false

            UALog.v("Notification expiration time is \"%s\"", expirationStr)
            try {
                val expiration = expirationStr.toLong() * 1000
                if (expiration < clock.currentTimeMillis()) {
                    return true
                }
            } catch (e: NumberFormatException) {
                UALog.d(e, "Ignoring malformed expiration time.")
            }

            return false
        }

    /**
     * Checks if the message is ping or not
     *
     * @return `true` if the message is a ping to test if
     * application is active, otherwise `false`
     */
    public val isPing: Boolean
        get() = data.containsKey(EXTRA_PING)

    /**
     * Gets an extra from the push bundle.
     *
     * @param key The extra key.
     * @return The extra.
     */
    public fun getExtra(key: String): String? {
        return data[key]
    }

    /**
     * Gets an extra from the push bundle.
     *
     * @param key The extra key.
     * @param defaultValue Default value if the value does not exist.
     * @return The extra or the default value if the extra does not exist.
     */
    public fun getExtra(key: String, defaultValue: String): String {
        return getExtra(key) ?: defaultValue
    }

    /**
     * Checks the message for Airship keys.
     *
     * @return `true` if the message contains any Airship keys.
     */
    public fun containsAirshipKeys(): Boolean {
        return data.keys.any { it.startsWith("com.urbanairship") }
    }

    /**
     * Gets the message's canonical push ID
     */
    public val canonicalPushId: String?
        get() = data[EXTRA_PUSH_ID]

    /**
     * Gets the rich push message ID
     */
    public val richPushMessageId: String?
        get() = data[EXTRA_RICH_PUSH_ID]

    /**
     * Gets the notification alert
     */
    public val alert: String?
        get() = data[EXTRA_ALERT]

    /**
     * Gets the push send ID
     */
    public val sendId: String?
        get() = data[EXTRA_SEND_ID]

    /**
     * Gets the push send metadata.
     */
    public val metadata: String?
        get() = data[EXTRA_METADATA]

    /**
     * Returns a bundle of all the push extras
     *
     * @return A bundle of all the push extras
     */
    public fun getPushBundle(): Bundle {
        return pushBundle ?: run {
            val bundle = Bundle()
            data.forEach { bundle.putString(it.key, it.value) }
            pushBundle = bundle
            bundle
        }
    }

    /**
     * Checks if the push is from Accengage and has content or not.
     */
    public val isAccengageVisiblePush: Boolean
        get() = data.containsKey(ACCENGAGE_CONTENT_KEY)

    /**
     * Checks if the push is from Accengage or not.
     */
    public val isAccengagePush: Boolean
        get() = data.containsKey(ACCENGAGE_ID_KEY)

    /**
     * Checks if the push is from Airship or not.
     */
    public val isAirshipPush: Boolean
        get() = data.containsKey(EXTRA_SEND_ID)
                || data.containsKey(EXTRA_PUSH_ID)
                || data.containsKey(EXTRA_METADATA)


    /**
     * Gets the push message's actions.
     */
    public val actions: Map<String, ActionValue>
        get() {
            val actionsPayload = data[EXTRA_ACTIONS]
            val actions = try {
                JsonValue.parseString(actionsPayload)
                    .map
                    ?.associate { it.key to ActionValue(it.value) }
                    ?.toMutableMap()
                    ?: mutableMapOf()
            } catch (e: JsonException) {
                UALog.e("Unable to parse action payload: %s", actionsPayload)
                return emptyMap()
            }

            if (!richPushMessageId.isNullOrEmpty()) {
                actions[MESSAGE_CENTER_ACTION] = ActionValue.wrap(richPushMessageId)
            }

            return actions.toMap()
        }

    /**
     * Gets the notification actions payload.
     */
    public val interactiveActionsPayload: String?
        get() = data[EXTRA_INTERACTIVE_ACTIONS]

    /**
     * Gets the notification action button type.
     */
    public val interactiveNotificationType: String?
        get() = data[EXTRA_INTERACTIVE_TYPE]

    /**
     * Gets the title of the notification.
     */
    public val title: String?
        get() = data[EXTRA_TITLE]

    /**
     * Gets the summary of the notification.
     */
    public val summary: String?
        get() = data[EXTRA_SUMMARY]

    /**
     * Gets the wearable payload.
     */
    public val wearablePayload: String?
        get() = data[EXTRA_WEARABLE]

    /**
     * Gets the style payload of the notification.
     */
    public val stylePayload: String?
        get() = data[EXTRA_STYLE]

    /**
     * Checks if the notification should only be displayed on the device.
     *
     * @return `true` if the notification should only be displayed on the device,
     * otherwise `false`
     *
     * Defaults to false.
     */
    public val isLocalOnly: Boolean
        get() {
            return data[EXTRA_LOCAL_ONLY].toBoolean()
        }


    /**
     * Gets the priority of the notification.
     * Defaults to 0.
     */
    public val priority: Int
        get() {
            try {
                val value = data[EXTRA_PRIORITY]
                if (value.isNullOrEmpty()) {
                    return 0
                }

                return value.toInt().coerceIn(MIN_PRIORITY, MAX_PRIORITY)
            } catch (_: NumberFormatException) {
                return 0
            }
        }

    /**
     * Gets the visibility of the notification for the lockscreen.
     * Defaults to 1 (VISIBILITY_PUBLIC).
     */
    public val visibility: Int
        get() {
            try {
                val value = data[EXTRA_VISIBILITY]
                if (value.isNullOrEmpty()) {
                    return VISIBILITY_PUBLIC
                }

                return value.toInt().coerceIn(MIN_VISIBILITY, MAX_VISIBILITY)
            } catch (_ : NumberFormatException) {
                return VISIBILITY_PUBLIC
            }
        }

    /**
     * Gets the public notification payload.
     */
    public val publicNotificationPayload: String?
        get() = data[EXTRA_PUBLIC_NOTIFICATION]

    /**
     * Gets the category of the notification.
     */
    public val category: String?
        get() = data[EXTRA_CATEGORY]

    /**
     * Gets the sound of the notification.
     *
     * @param context The application context.
     * @return The sound of the notification.
     */
    @Deprecated("This setting does not work on Android O+. Applications are encouraged to use {@link NotificationChannelRegistry} instead.")
    public fun getSound(context: Context): Uri? {
        sound?.let { return sound }
        val notificationSoundName = data[EXTRA_SOUND] ?: return sound

        val id = context.resources.getIdentifier(notificationSoundName, "raw", context.packageName)
        if (id != 0) {
            sound = Uri.parse("android.resource://" + context.packageName + "/" + id)
        } else if (DEFAULT_SOUND_NAME != notificationSoundName) {
            // Do not log a warning for the "default" name. Android plays the default sound if no sound
            // is provided.
            UALog.w(
                "PushMessage - unable to find notification sound with name: %s",
                notificationSoundName
            )
        }

        return sound
    }

    /**
     * Gets the notification icon color.
     *
     * @return The color of the icon.
     */
    public fun getIconColor(defaultColor: Int): Int {
        val colorString = data[EXTRA_ICON_COLOR] ?: return defaultColor

        return try {
            Color.parseColor(colorString)
        } catch (e: IllegalArgumentException) {
            UALog.w(
                "Unrecognized icon color string: %s. Using default color: %s",
                colorString,
                defaultColor
            )
            defaultColor
        }
    }

    /**
     * Gets the notification icon image.
     *
     * @return The integer resource of the icon image.
     */
    @DrawableRes
    public fun getIcon(context: Context, defaultIcon: Int): Int {
        val resourceString = data[EXTRA_ICON] ?: return defaultIcon

        val iconId = context.resources.getIdentifier(resourceString, "drawable", context.packageName)
        return if (iconId != 0) {
            iconId
        } else {
            UALog.w(
                "PushMessage - unable to find icon drawable with name: %s. Using default icon: %s",
                resourceString,
                defaultIcon
            )
            defaultIcon
        }
    }

    /**
     * Gets the Live Update payload, if present.
     */
    public val liveUpdatePayload: String?
        get() = data[EXTRA_LIVE_UPDATE]

    /**
     * Returns the notification tag that should be used when posting the notification.
     */
    public val notificationTag: String?
        get() = data[EXTRA_NOTIFICATION_TAG]

    /**
     * Returns the notification channel that should be used when posting the notification.
     */
    public val notificationChannel: String?
        get() = data[EXTRA_NOTIFICATION_CHANNEL]

    /**
     * Returns if the notification should be displayed or suppressed in the foreground.
     */
    public val isForegroundDisplayable: Boolean
        get() {
            return data[EXTRA_FOREGROUND_DISPLAY]?.toBoolean() ?: true
        }

    /**
     * Returns the notification channel that should be used when posting the notification or the
     * default channel if its not defined.
     *
     * @return Either the notification channel or the default channel if the channel is not defined.
     */
    public fun getNotificationChannel(defaultChannel: String?): String? {
        return data[EXTRA_NOTIFICATION_CHANNEL] ?: defaultChannel
    }

    /** Returns the channel ID (APID) if present. */
    internal fun isChannelIdNullOrMatching(): Boolean {
        val apid = data[EXTRA_APID]
        return if (apid != null) {
            apid == Airship.channel.id
        } else {
            true
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) { return true }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as PushMessage

        return this.data == that.data
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    override fun toString(): String {
        return data.toString()
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeBundle(getPushBundle())
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrapOpt(data)

    /**
     * Checks if the message is to update remote-data or not.
     * @hide
     */
    internal val isRemoteDataUpdate: Boolean
        get() = data.containsKey(REMOTE_DATA_UPDATE_KEY)

    /**
     * Checks if the push message contains a key.
     *
     * @param key The key to check.
     * @return `true` if the push message contains a value at the key, otherwise `false`.
     */
    public fun containsKey(key: String): Boolean = data.containsKey(key)

    public companion object {

        /**
         * The rich push extra that contains the rich push message ID.
         */
        public const val EXTRA_RICH_PUSH_ID: String = "_uamid"

        /**
         * The ping extra indicates a push meant to test whether the application is active
         */
        public const val EXTRA_PING: String = "com.urbanairship.push.PING"

        /**
         * The alert extra holds the string sent in the "alert" field of an Airship
         * Push Notification.
         */
        public const val EXTRA_ALERT: String = "com.urbanairship.push.ALERT"

        /**
         * The push ID extra holds the unique push ID sent in an Airship
         * Push Notification. This is most commonly referred to as the "Send ID"
         * at Airship.
         */
        public const val EXTRA_SEND_ID: String = "com.urbanairship.push.PUSH_ID"

        /**
         * The push metadata extra holds the encrypted push identifiers sent in an
         * Airship Push Notification. Possible data includes send, push, and group IDs.
         */
        public const val EXTRA_METADATA: String = "com.urbanairship.metadata"

        /**
         * The actions extra key holds the payload of actions to be performed with the
         * push.
         */
        public const val EXTRA_ACTIONS: String = "com.urbanairship.actions"

        /**
         * The Live Update payload.
         */
        public const val EXTRA_LIVE_UPDATE: String = "com.urbanairship.live_update"

        /**
         * The extra key for the payload of Airship actions to be run when an
         * interactive notification action button is opened.
         */
        public const val EXTRA_INTERACTIVE_ACTIONS: String = "com.urbanairship.interactive_actions"

        /**
         * The extra key for the interactive notification group that will be displayed with a push.
         */
        public const val EXTRA_INTERACTIVE_TYPE: String = "com.urbanairship.interactive_type"

        /**
         * The extra key for the title of the notification.
         */
        public const val EXTRA_TITLE: String = "com.urbanairship.title"

        /**
         * The extra key for the summary of the notification.
         */
        public const val EXTRA_SUMMARY: String = "com.urbanairship.summary"

        /**
         * The extra key for the wearable payload.
         */
        public const val EXTRA_WEARABLE: String = "com.urbanairship.wearable"

        /**
         * The extra key for the style of the notification.
         */
        public const val EXTRA_STYLE: String = "com.urbanairship.style"

        /**
         * The extra key indicates if the notification should only be displayed on the device.
         */
        public const val EXTRA_LOCAL_ONLY: String = "com.urbanairship.local_only"

        /**
         * The extra key indicates the icon color.
         */
        public const val EXTRA_ICON_COLOR: String = "com.urbanairship.icon_color"

        /**
         * The extra key indicates the name of an icon to use from an app's drawable resources.
         */
        public const val EXTRA_ICON: String = "com.urbanairship.icon"

        /**
         * The extra key for the priority of the notification. Acceptable values range from PRIORITY_MIN
         * (-2) to PRIORITY_MAX (2).
         *
         *
         * Defaults to 0.
         */
        public const val EXTRA_PRIORITY: String = "com.urbanairship.priority"

        /**
         * The extra key for the sound of the notification.
         *
         */
        @Deprecated("This setting does not work on Android O+.")
        public const val EXTRA_SOUND: String = "com.urbanairship.sound"

        /**
         * The minimum priority value for the notification.
         */
        public const val MIN_PRIORITY: Int = -2

        /**
         * The maximum priority value for the notification.
         */
        public const val MAX_PRIORITY: Int = 2

        /**
         * The extra key for the notification's visibility in the lockscreen. Acceptable values are:
         * VISIBILITY_PUBLIC (1), VISIBILITY_PRIVATE (0) or VISIBILITY_SECRET (-1).
         */
        public const val EXTRA_VISIBILITY: String = "com.urbanairship.visibility"

        /**
         * The minimum visibility value for the notification in the lockscreen.
         */
        public const val MIN_VISIBILITY: Int = -1

        /**
         * The maximum visibility value for the notification in the lockscreen.
         */
        public const val MAX_VISIBILITY: Int = 1

        /**
         * Shows the notification's full content in the lockscreen. This is the default visibility.
         */
        public const val VISIBILITY_PUBLIC: Int = 1

        /**
         * The extra key for the public notification payload.
         */
        public const val EXTRA_PUBLIC_NOTIFICATION: String = "com.urbanairship.public_notification"

        /**
         * The extra key for the category of the notification.
         */
        public const val EXTRA_CATEGORY: String = "com.urbanairship.category"

        /**
         * The push ID extra is the ID assigned to a push at the time it is sent.
         * Each API call will result in a unique push ID, so all notifications that are part of a
         * multicast push will have the same push ID.
         */
        public const val EXTRA_PUSH_ID: String = "com.urbanairship.push.CANONICAL_PUSH_ID"

        /**
         * The EXPIRATION extra is a time expressed in seconds since the Epoch after which, if specified, the
         * notification should not be delivered. It is removed from the notification before delivery to the
         * client. If not present, notifications may be delivered arbitrarily late.
         */
        public const val EXTRA_EXPIRATION: String = "com.urbanairship.push.EXPIRATION"

        /**
         * The extra key for the the legacy in-app message payload.
         */
        public const val EXTRA_IN_APP_MESSAGE: String = "com.urbanairship.in_app"

        /**
         * The extra key for the tag to be used when posting a notification.
         */
        public const val EXTRA_NOTIFICATION_TAG: String = "com.urbanairship.notification_tag"

        /**
         * The extra key for the channel to be used when posting a notification.
         */
        public const val EXTRA_NOTIFICATION_CHANNEL: String =
            "com.urbanairship.notification_channel"

        /**
         * The extra key for the delivery priority.
         */
        public const val EXTRA_DELIVERY_PRIORITY: String = "com.urbanairship.priority"

        /** The extra key for the channel ID. */
        public const val EXTRA_APID: String = "com.urbanairship.push.APID"

        /**
         * Constant for the extra [.EXTRA_DELIVERY_PRIORITY] that indicates the push is high priority.
         */
        public const val PRIORITY_HIGH: String = "high"

        /**
         * The extra key to control the notification display in the foreground.
         */
        public const val EXTRA_FOREGROUND_DISPLAY: String = "com.urbanairship.foreground_display"

        /**
         * Accengage constant used to determine if the push has Accengage content or not.
         */
        private const val ACCENGAGE_CONTENT_KEY = "a4scontent"

        /**
         * Accengage constant used to determine if the push is Accengage or not.
         */
        private const val ACCENGAGE_ID_KEY = "a4sid"

        /**
         * The Push key indicating that a remote data update is required.
         * @hide
         */
        internal const val REMOTE_DATA_UPDATE_KEY: String = "com.urbanairship.remote-data.update"

        /**
         * Default sound name.
         */
        private const val DEFAULT_SOUND_NAME = "default"

        private const val MESSAGE_CENTER_ACTION = "^mc"

        /**
         * Gets the push message from the intent if available.
         *
         * @param intent The intent.
         * @return The intent's PushMessage or null if the intent does not contain a PushMessage.
         * @hide
         */
        public fun fromIntent(intent: Intent?): PushMessage? {
            if (intent == null) { return null }

            return try {
                intent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE)?.let { PushMessage(it) }
            } catch (e: BadParcelableException) {
                UALog.e(e, "Failed to parse push message from intent.")
                null
            }
        }

        /**
         * Parcel Creator for push messages.
         *
         * @hide
         */
        @JvmField
        public val CREATOR: Parcelable.Creator<PushMessage> =
            object : Parcelable.Creator<PushMessage> {
                override fun createFromParcel(`in`: Parcel): PushMessage {
                    val bundle = `in`.readBundle(PushMessage::class.java.classLoader)
                    return PushMessage(bundle ?: Bundle())
                }

                override fun newArray(size: Int): Array<PushMessage?> {
                    return arrayOfNulls(size)
                }
            }

        /**
         * Creates a push message from a json value.
         *
         * @param jsonValue The json value.
         * @return The push message.
         */
        public fun fromJsonValue(jsonValue: JsonValue): PushMessage {
            val data = jsonValue
                .optMap()
                .associate {
                    val value = if (it.value.isString) it.value.optString() else it.value.toString()
                    it.key to value
                }

            return PushMessage(data)
        }
    }
}
