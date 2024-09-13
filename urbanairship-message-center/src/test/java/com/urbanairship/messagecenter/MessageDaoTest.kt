package com.urbanairship.messagecenter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import java.util.UUID
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class MessageDaoTest {

    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedTestDispatcher = UnconfinedTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = MessageDatabase.createInMemoryDatabase(context, unconfinedTestDispatcher)
    private val messageDao: MessageDao = db.dao

    private val entity = requireNotNull(
        MessageEntity.createMessageFromPayload(MESSAGE_ID_1, messageJson)
    )
    private val entity2 = requireNotNull(
        MessageEntity.createMessageFromPayload(MESSAGE_ID_2, messageJson2)
    )

    @Before
    @Throws(JsonException::class)
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Sanity check
        runBlocking {
            assertEquals(0, messageDao.getMessages().size)
        }
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    public fun testInsert(): TestResult = runTest {
        messageDao.insert(entity)
        assertEquals(1, messageDao.getMessages().size)
    }

    @Test
    public fun testInsertsReplaceOnConflict(): TestResult = runTest  {
        // Insert duplicate messages.
        insertMessage("foo", messageJson)
        insertMessage("foo", messageJson)

        val messages = messageDao.getMessages()
        assertEquals(1, messages.size)
        assertEquals(MESSAGE_TITLE_1, messages[0].title)

        // Insert another with same ID, but different data.
        insertMessage("foo", messageJson2)

        val updatedMessages = messageDao.getMessages()
        assertEquals(1, updatedMessages.size)
        assertEquals(MESSAGE_TITLE_2, updatedMessages[0].title)
    }

    @Test
    public fun testGetMessages(): TestResult = runTest  {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.getMessages().size)

        val messageEntities = messageDao.getMessages()
        assertEquals(MESSAGE_ID_1, messageEntities[0].messageId)
        assertEquals(MESSAGE_ID_2, messageEntities[1].messageId)
    }

    @Test
    public fun testGetReadMessages(): TestResult = runTest  {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.getMessages().size)

        messageDao.markMessagesRead(listOf(MESSAGE_ID_1))

        val messageEntities = messageDao.getLocallyReadMessages()
        assertEquals(1, messageEntities.size)
        assertEquals(MESSAGE_ID_1, messageEntities[0].messageId)
    }

    @Test
    public fun testGetDeletedMessages(): TestResult = runTest  {
        messageDao.insert(entity)
        messageDao.insert(entity2)
        assertEquals(2, messageDao.getMessages().size)

        messageDao.markMessagesDeleted(listOf(MESSAGE_ID_1))

        val messageEntities = messageDao.getLocallyDeletedMessages()
        assertEquals(1, messageEntities.size)
        assertEquals(MESSAGE_ID_1, messageEntities[0].messageId)
    }

    @Test
    public fun testDeleteTooManyMessages(): TestResult = runTest  {
        val messageIds = insertMessages(2000)
        assertEquals(2000, messageDao.getMessages().size)

        messageDao.deleteMessages(messageIds)

        assertEquals(0, messageDao.getMessages().size)
    }

    @Suppress("SameParameterValue") // count is always 2000
    private suspend fun insertMessages(count: Int): List<String> {
        val messageIds: MutableList<String> = ArrayList()
        for (i in 0 until count) {
            messageIds.add(insertMessage(UUID.randomUUID().toString(), messageJson))
        }
        return messageIds
    }

    private suspend fun insertMessage(id: String, json: JsonValue): String {
        val message = requireNotNull(MessageEntity.createMessageFromPayload(id, json))
        messageDao.insert(message)
        return message.getMessageId()
    }

    private companion object {
        private const val MESSAGE_ID_1 = "1_message_id"
        private const val MESSAGE_ID_2 = "2_message_id"
        private const val MESSAGE_TITLE_1 = "Message 1 Title"
        private const val MESSAGE_TITLE_2 = "Message 2 Title"

        private val messagePayload1 = """
            {
              "message_id": "$MESSAGE_ID_1",
              "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
              "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
              "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
              "message_reporting": {
                "message_id": "$MESSAGE_ID_1"
              },
              "unread": true,
              "message_sent": "2010-09-05 12:13 -0000",
              "title": "$MESSAGE_TITLE_1",
              "extra": {
                "some_key": "some_value"
              },
              "content_type": "text/html",
              "content_size": "128"
            }
        """.trimIndent()

        private val messagePayload2 = """
            {
              "message_id": "$MESSAGE_ID_2",
              "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
              "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
              "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
              "message_reporting": {
                "message_id": "$MESSAGE_ID_2"
              },
              "unread": true,
              "message_sent": "2010-09-05 12:13 -0000",
              "title": "$MESSAGE_TITLE_2",
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
