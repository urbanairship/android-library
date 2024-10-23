
package com.urbanairship.messagecenter;

import android.os.Bundle
import android.os.Parcelable
import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.toBundle
import com.urbanairship.messagecenter.util.JsonMapParceler
import com.urbanairship.util.DateUtils
import java.util.Date
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith

/**
 * Message Center Message data.
 *
 * @property id The Airship message ID. May be used to match an incoming push notification to a specific `Message`.
 * @property title The message title.
 * @property bodyUrl The URL for the message body. URL may only be accessed with Basic Auth credentials set to the user ID and password.
 * @property sentDate The date and time the message was sent (UTC).
 * @property expirationDate (Optional) The date and time the message will expire (UTC).
 */
@Parcelize
public class Message internal constructor(
    public val id: String,
    public val title: String,
    public val bodyUrl: String,
    public val sentDate: Date,
    public val expirationDate: Date?,
    public val isUnread: Boolean,
    internal val extrasJson: @WriteWith<JsonMapParceler> JsonMap?,
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
        extrasJson?.optionalField(EXTRA_SUBTITLE)
    }

    /** Returns the message extras as a `Bundle`. */
    @IgnoredOnParcel
    public val extras: Bundle?
        get() = extrasJson?.toBundle()

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

        if (id != other.id) return false
        if (title != other.title) return false
        if (extrasJson != other.extrasJson) return false
        if (bodyUrl != other.bodyUrl) return false
        if (sentDate != other.sentDate) return false
        if (expirationDate != other.expirationDate) return false
        if (isUnread != other.isUnread) return false
        if (messageUrl != other.messageUrl) return false
        if (reporting != other.reporting) return false
        if (rawMessageJson != other.rawMessageJson) return false
        if (isUnreadClient != other.isUnreadClient) return false
        if (isDeletedClient != other.isDeletedClient) return false
        if (isRead != other.isRead) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + extrasJson.hashCode()
        result = 31 * result + bodyUrl.hashCode()
        result = 31 * result + sentDate.hashCode()
        result = 31 * result + (expirationDate?.hashCode() ?: 0)
        result = 31 * result + isUnread.hashCode()
        result = 31 * result + messageUrl.hashCode()
        result = 31 * result + (reporting?.hashCode() ?: 0)
        result = 31 * result + rawMessageJson.hashCode()
        result = 31 * result + isUnreadClient.hashCode()
        result = 31 * result + isDeletedClient.hashCode()
        result = 31 * result + isRead.hashCode()
        return result
    }

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
                    extrasJson = json.optionalMap(KEY_EXTRAS),
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
