/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.Message.EXTRA_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_BODY_URL_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_EXPIRY_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_ID_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_READ_URL_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_REPORTING_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_SENT_KEY
import com.urbanairship.messagecenter.Message.MESSAGE_URL_KEY
import com.urbanairship.messagecenter.Message.TITLE_KEY
import com.urbanairship.messagecenter.Message.UNREAD_KEY
import com.urbanairship.messagecenter.Message.create
import com.urbanairship.util.DateUtils

public object MessageCenterTestUtils {

    private var messageEntities: List<MessageEntity>? = null
    private lateinit var messageDao: MessageDao
    private lateinit var messageDatabase: MessageDatabase
    public fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        messageDatabase = MessageDatabase.createInMemoryDatabase(context)
        messageDao = messageDatabase.dao
    }

    @JvmOverloads
    public fun insertMessage(
        messageId: String?,
        extras: Map<String, String>? = null,
        expired: Boolean = false
    ) {
        val message = createMessage(messageId, extras, expired)
        val entity = requireNotNull(MessageEntity.createMessageFromPayload(messageId, message.rawMessageJson))
        messageDao.insert(entity)
        messageEntities = messageDao.messages
    }

    public fun createMessage(
        messageId: String?,
        extras: Map<String, String>?,
        expired: Boolean
    ): Message {
        val payload: MutableMap<String, Any?> = mutableMapOf(
            MESSAGE_ID_KEY to messageId,
            MESSAGE_REPORTING_KEY to JsonValue.wrap(messageId),
            MESSAGE_BODY_URL_KEY to "https://go.urbanairship.com/api/user/tests/messages/$messageId/body/",
            MESSAGE_READ_URL_KEY to "https://go.urbanairship.com/api/user/tests/messages/$messageId/read/",
            MESSAGE_URL_KEY to "https://go.urbanairship.com/api/user/tests/messages/$messageId",
            TITLE_KEY to "$messageId title",
            UNREAD_KEY to true,
            MESSAGE_SENT_KEY to DateUtils.createIso8601TimeStamp(System.currentTimeMillis())
        )
        if (extras != null) {
            payload[EXTRA_KEY] = extras
        }
        if (expired) {
            payload[MESSAGE_EXPIRY_KEY] = DateUtils.createIso8601TimeStamp(0)
        }

        return requireNotNull(create(JsonValue.wrapOpt(payload), true, false))
    }
}
