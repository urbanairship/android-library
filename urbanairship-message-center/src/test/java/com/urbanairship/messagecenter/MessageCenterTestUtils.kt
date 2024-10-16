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

        expirationDate?.let {
            payload[MESSAGE_EXPIRY_KEY] = DateUtils.createIso8601TimeStamp(it.time)
        }

        return requireNotNull(create(JsonValue.wrapOpt(payload), true, false))
    }
}
