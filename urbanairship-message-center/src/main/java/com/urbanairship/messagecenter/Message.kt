
package com.urbanairship.messagecenter;

import android.os.Parcelable
import com.urbanairship.UALog
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.util.DateUtils
import java.util.Date
import java.util.Objects
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

/**
 * Message Center Message data.
 *
 * @property id The Airship message ID. May be used to match an incoming push notification to a specific `Message`.
 * @property title The message title.
 * @property bodyUrl The URL for the message body. URL may only be accessed with Basic Auth credentials set to the user ID and password.
 * @property sentDate The date and time the message was sent (UTC).
 * @property expirationDate (Optional) The date and time the message will expire (UTC).
 * @property extras (Optional) Additional key-value pairs associated with the message.
 */
@Parcelize
public class Message internal constructor(
    public val id: String,
    public val title: String,
    public val bodyUrl: String,
    public val sentDate: Date,
    public val expirationDate: Date?,
    public val isUnread: Boolean,
    public val extras: Map<String, String?>?,
    internal val messageUrl: String,
    internal val reporting: JsonValue?,
    internal val rawMessageJson: JsonValue,
    internal var isUnreadClient: Boolean = isUnread,
    internal var isDeletedClient: Boolean,
) : Parcelable {

    /** Indicates whether the message has been read. */
    @IgnoredOnParcel
    public val isRead: Boolean = !isUnreadClient

    /** Indicates whether the message has been expired. */
    @IgnoredOnParcel
    public val isExpired: Boolean
        get() = expirationDate?.before(Date()) ?: false

    /** Indicates whether the message has been deleted. */
    @IgnoredOnParcel
    public val isDeleted: Boolean
        get() = isDeletedClient

    /** Optional list icon URL for this `Message`. */
    @IgnoredOnParcel
    public val listIconUrl: String? by lazy {
        try {
            rawMessageJson.optMap().optionalMap(KEY_ICONS)?.optionalField(KEY_LIST_ICON)
        } catch (e: Exception) {
            UALog.w(e, "Failed to get Message Center Message list icon!")
            null
        }
    }

    /**
     * Optional subtitle for this `Message`.
     *
     * This is a custom extra that can be set when creating the message, using the key:
     * `com.urbanairship.listing.field1`
     */
    @IgnoredOnParcel
    public val subtitle: String? by lazy {
        extras?.get(EXTRA_SUBTITLE)
    }

    // TODO: Remove when deprecated methods below are removed
    private val inbox: Inbox
        get() = MessageCenter.shared().inbox

    /** Marks this `Message` as read. */
    @Deprecated(
        "Replace with Inbox.markMessagesRead(message.id, ...)",
        ReplaceWith("MessageCenter.shared().inbox.markMessagesRead(message.id)")
    )
    public fun markRead() {
        if (isUnreadClient) {
            isUnreadClient = false
            inbox.markMessagesRead(this.id)
        }
    }

    /** Marks this `Message` as unread */
    @Deprecated(
        "Replace with Inbox.markMessagesUnread(message.id, ...)",
        ReplaceWith("MessageCenter.shared().inbox.markMessagesUnread(message.id)")
    )
    public fun markUnread() {
        if (!isUnreadClient) {
            isUnreadClient = true
            inbox.markMessagesRead(this.id)
        }
    }

    /** Deletes this `Message` */
    @Deprecated(
        "Replace with Inbox.deleteMessages(message.id, ...)",
        ReplaceWith("MessageCenter.shared().inbox.deleteMessages(message.id)")
    )
    public fun delete() {
        if (!isDeletedClient) {
            isDeletedClient = true
            inbox.deleteMessages(this.id)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        return id == other.id &&
            title == other.title &&
            extras == other.extras &&
            bodyUrl == other.bodyUrl &&
            sentDate == other.sentDate &&
            expirationDate == other.expirationDate &&
            isUnread == other.isUnread &&
            messageUrl == other.messageUrl &&
            reporting == other.reporting &&
            rawMessageJson == other.rawMessageJson &&
            isUnreadClient == other.isUnreadClient &&
            isDeletedClient == other.isDeletedClient &&
            isRead == other.isRead
    }

    override fun hashCode(): Int = Objects.hash(
        id,
        title,
        extras,
        bodyUrl,
        sentDate,
        expirationDate,
        isUnread,
        messageUrl,
        reporting,
        rawMessageJson,
        isUnreadClient,
        isDeletedClient,
        isRead
    )

    public companion object {
        internal const val KEY_ID: String = "message_id"
        internal const val KEY_TITLE: String = "title"
        internal const val KEY_EXTRAS: String = "extra"
        internal const val KEY_BODY_URL: String = "message_body_url"
        internal const val KEY_SENT_DATE: String = "message_sent"
        internal const val KEY_EXPIRATION_DATE: String = "message_expiry"
        internal const val KEY_IS_UNREAD: String = "unread"
        internal const val KEY_MESSAGE_URL: String = "message_url"
        internal const val KEY_MESSAGE_READ_URL: String = "message_read_url"
        internal const val KEY_MESSAGE_REPORTING: String = "message_reporting"
        internal const val KEY_ICONS: String = "icons"
        internal const val KEY_LIST_ICON: String = "list_icon"
        internal const val EXTRA_SUBTITLE: String = "com.urbanairship.listing.field1"

        internal fun create(payload: JsonValue, isUnreadClient: Boolean, isDeleted: Boolean): Message? =
            try {
                val json = payload.requireMap()

                Message(
                    id = json.requireField(KEY_ID),
                    title = json.requireField(KEY_TITLE),
                    extras = json.optionalMap(KEY_EXTRAS)?.map?.mapValues { it.value.coerceString() },
                    bodyUrl = json.requireField(KEY_BODY_URL),
                    sentDate = json.optionalField<String>(KEY_SENT_DATE)
                        ?.let { Date(DateUtils.parseIso8601(it)) } ?: Date(),
                    expirationDate = json.optionalField<String>(KEY_EXPIRATION_DATE)
                        ?.let { Date(DateUtils.parseIso8601(it, Long.MAX_VALUE)) },
                    isUnread = json.optionalField(KEY_IS_UNREAD) ?: false,
                    messageUrl = json.requireField(KEY_MESSAGE_URL),
                    reporting = json[KEY_MESSAGE_REPORTING],
                    rawMessageJson = json.toJsonValue(),
                    isUnreadClient = isUnreadClient,
                    isDeletedClient = isDeleted
                )
            } catch (e: Exception) {
                UALog.e(e, "Failed to create message from payload: $payload")
                null
            }


        @JvmStatic
        public val SENT_DATE_COMPARATOR: Comparator<Message> = Comparator { lhs, rhs ->
            if (rhs.sentDate == lhs.sentDate) {
                lhs.id.compareTo(rhs.id)
            } else {
                rhs.sentDate.compareTo(lhs.sentDate)
            }
        }
    }
}
