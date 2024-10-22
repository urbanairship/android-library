
package com.urbanairship.messagecenter;

import android.os.Bundle
import com.urbanairship.UALog
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.toBundle
import com.urbanairship.util.DateUtils
import java.util.Date

/**
 * Message Center Message data.
 *
 * @property id The Airship message ID. May be used to match an incoming push notification to a specific `Message`.
 * @property title The message title.
 * @property extras (Optional) Message extras.
 * @property bodyUrl The URL for the message body. URL may only be accessed with Basic Auth credentials set to the user ID and password.
 * @property sentDate The date and time the message was sent (UTC).
 * @property expirationDate (Optional) The date and time the message will expire (UTC).
 */
public class Message internal constructor(
    public val id: String,
    public val title: String,
    public val extras: Bundle,
    public val bodyUrl: String,
    public val sentDate: Date,
    public val expirationDate: Date?,
    public val isUnread: Boolean,
    internal val messageUrl: String,
    internal val reporting: JsonValue?,
    internal val rawMessageJson: JsonMap,
    internal var isUnreadClient: Boolean = isUnread,
    internal var isDeletedClient: Boolean,
) {

    /** Indicates whether the message has been read. */
    public val isRead: Boolean = !isUnreadClient

    /** Indicates whether the message has been expired. */
    public val isExpired: Boolean
        get() = expirationDate?.before(Date()) ?: false

    /** Indicates whether the message has been deleted. */
    public val isDeleted: Boolean
        get() = isDeletedClient

    /** Optional list icon URL for this `Message`. */
    public val listIconUrl: String? by lazy {
        try {
            rawMessageJson.optionalMap(KEY_ICONS)?.optionalField(KEY_LIST_ICON)
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
    public val subtitle: String? by lazy {
        extras.getString(EXTRA_SUBTITLE)
    }

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

    private val inbox: Inbox
        get() = MessageCenter.shared().inbox

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
                    extras = json.optionalMap(KEY_EXTRAS)?.toBundle() ?: Bundle.EMPTY,
                    bodyUrl = json.requireField(KEY_BODY_URL),
                    sentDate = json.optionalField<String>(KEY_SENT_DATE)
                        ?.let { Date(DateUtils.parseIso8601(it)) } ?: Date(),
                    expirationDate = json.optionalField<String>(KEY_EXPIRATION_DATE)
                        ?.let { Date(DateUtils.parseIso8601(it, Long.MAX_VALUE)) },
                    isUnread = json.optionalField(KEY_IS_UNREAD) ?: false,
                    messageUrl = json.requireField(KEY_MESSAGE_URL),
                    reporting = json[KEY_MESSAGE_REPORTING],
                    rawMessageJson = json,
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
