/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.core.Message
import com.urbanairship.messagecenter.core.MessageDao
import com.urbanairship.messagecenter.core.MessageDatabase
import com.urbanairship.messagecenter.core.MessageEntity
import com.urbanairship.util.DateUtils
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

public object MessageCenterTestUtils {

    private var messageEntities: List<MessageEntity>? = null
    private lateinit var messageDao: MessageDao
    private lateinit var messageDatabase: MessageDatabase
    public fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        messageDatabase = MessageDatabase.createInMemoryDatabase(context, Dispatchers.IO)
        messageDao = messageDatabase.dao
    }

    @JvmOverloads
    public fun insertMessage(
        messageId: String?,
        extras: Map<String, String>? = null,
        expired: Boolean = false,
        expirationDate: Date? = null
    ) {
        val expiration = expirationDate ?: if (expired) Date(0) else null
        val message = createMessage(messageId, extras, expiration)
        val entity = requireNotNull(MessageEntity.createMessageFromPayload(messageId, message.rawMessageJson))
        runBlocking {
            messageDao.insert(entity)
            messageEntities = messageDao.getMessages()
        }
    }

    public fun createMessage(
        messageId: String?,
        extras: Map<String, String>? = null,
        expirationDate: Date? = null
    ): Message {
        val payload: MutableMap<String, Any?> = mutableMapOf(
            Message.KEY_ID to messageId,
            Message.KEY_MESSAGE_REPORTING to JsonValue.wrap(messageId),
            Message.KEY_BODY_URL to "https://go.urbanairship.com/api/user/tests/messages/$messageId/body/",
            Message.KEY_MESSAGE_READ_URL to "https://go.urbanairship.com/api/user/tests/messages/$messageId/read/",
            Message.KEY_MESSAGE_URL to "https://go.urbanairship.com/api/user/tests/messages/$messageId",
            Message.KEY_TITLE to "$messageId title",
            Message.KEY_IS_UNREAD to true,
            Message.KEY_SENT_DATE to DateUtils.createIso8601TimeStamp(System.currentTimeMillis())
        )
        if (extras != null) {
            payload[Message.KEY_EXTRAS] = extras
        }

        expirationDate?.let {
            payload[Message.KEY_EXPIRATION_DATE] = DateUtils.createIso8601TimeStamp(it.time)
        }

        return requireNotNull(Message.create(JsonValue.wrapOpt(payload), true, false))
    }
}
