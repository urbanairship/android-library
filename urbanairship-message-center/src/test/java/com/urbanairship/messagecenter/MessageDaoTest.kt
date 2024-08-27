package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.util.UUID
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class MessageDaoTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = MessageDatabase.createInMemoryDatabase(context)
    private val messageDao: MessageDao = db.dao

    private val entity = MessageEntity.createMessageFromPayload(messageId, messageJson)
    private val entity2 = MessageEntity.createMessageFromPayload(messageId2, messageJson2)

    @Before
    @Throws(JsonException::class)
    public fun setUp() {
        // Sanity check
        assertEquals(0, messageDao.messages.size)
    }

    @After
    public fun tearDown() {
        db.close()
    }

    @Test
    public fun testInsert() {
        messageDao.insert(entity)
        assertEquals(1, messageDao.messages.size)
    }

    @Test
    public fun testInsertsReplaceOnConflict() {
        // Insert duplicate messages.
        insertMessage("foo", messageJson)
        insertMessage("foo", messageJson)
        assertEquals(1, messageDao.messages.size)
        assertEquals(messageTitle1, messageDao.messages[0].title)

        // Insert another with same ID, but different data.
        insertMessage("foo", messageJson2)
        assertEquals(1, messageDao.messages.size)
        assertEquals(messageTitle2, messageDao.messages[0].title)
    }

    @Test
    public fun testGetMessages() {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.messages.size)

        val messageEntities = messageDao.messages
        assertEquals(messageId, messageEntities[0].messageId)
        assertEquals(messageId2, messageEntities[1].messageId)
    }

    @Test
    public fun testGetReadMessages() {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.messages.size)

        messageDao.markMessagesRead(listOf(messageId))

        val messageEntities = messageDao.locallyReadMessages
        assertEquals(1, messageEntities.size)
        assertEquals(messageId, messageEntities[0].messageId)
    }

    @Test
    public fun testGetDeletedMessages() {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.messages.size)

        messageDao.markMessagesDeleted(listOf(messageId))

        val messageEntities = messageDao.locallyDeletedMessages
        assertEquals(1, messageEntities.size)
        assertEquals(messageId, messageEntities[0].messageId)
    }

    @Test
    public fun testDeleteTooManyMessages() {
        val messageIds = insertMessages(2000)
        assertEquals(2000, messageDao.messages.size)

        messageDao.deleteMessages(messageIds)
        assertEquals(0, messageDao.messages.size)
    }

    @Suppress("SameParameterValue") // count is always 2000
    private fun insertMessages(count: Int): List<String> {
        val messageIds: MutableList<String> = ArrayList()
        for (i in 0 until count) {
            messageIds.add(insertMessage(UUID.randomUUID().toString(), messageJson))
        }
        return messageIds
    }

    private fun insertMessage(id: String, json: JsonValue): String {
        val message = requireNotNull(MessageEntity.createMessageFromPayload(id, json))
        messageDao.insert(message)
        return message.getMessageId()
    }

    private companion object {
        private const val messageId = "1_message_id"
        private const val messageId2 = "2_message_id"
        private const val messageTitle1 = "Message 1 Title"
        private const val messageTitle2 = "Message 2 Title"

        @Language("JSON")
        private val messagePayload1 = """
            {
              "message_id": "$messageId",
              "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
              "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
              "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
              "message_reporting": {
                "message_id": "$messageId"
              },
              "unread": true,
              "message_sent": "2010-09-05 12:13 -0000",
              "title": "$messageTitle1",
              "extra": {
                "some_key": "some_value"
              },
              "content_type": "text/html",
              "content_size": "128"
            }
        """.trimIndent()

        @Language("JSON")
        private val messagePayload2 = """
            {
              "message_id": "$messageId2",
              "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
              "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
              "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
              "message_reporting": {
                "message_id": "$messageId2"
              },
              "unread": true,
              "message_sent": "2010-09-05 12:13 -0000",
              "title": "$messageTitle2",
              "extra": {
                "some_key": "some_value"
              },
              "content_type": "text/html",
              "content_size": "128"
            }
        """.trimIndent()

        private val messageJson = JsonValue.parseString(messagePayload1)
        private val messageJson2 = JsonValue.parseString(messagePayload2)
    }
}
