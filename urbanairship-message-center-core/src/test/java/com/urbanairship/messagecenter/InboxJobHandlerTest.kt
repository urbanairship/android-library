/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.Airship
import com.urbanairship.Platform
import com.urbanairship.android.layout.info.LayoutInfo
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.iam.content.AirshipLayout
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.messagecenter.InboxJobHandler.Companion.LAST_MESSAGE_REFRESH_TIME
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class InboxJobHandlerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val testDispatcher = StandardTestDispatcher()

    private val dataStore: PreferenceDataStore = PreferenceDataStore.inMemoryStore(context)

    private val mockMessageDao = mockk<MessageDao>(relaxUnitFun = true) {
        coEvery { getMessageIds() } returns mutableListOf()
        coEvery { messageExists(any()) } returns false
        coEvery { getLocallyReadMessages() } returns emptyList()
        coEvery { getLocallyDeletedMessages() } returns emptyList()
    }
    private val mockInboxApiClient = mockk<InboxApiClient>()

    private val userCredentials = UserCredentials("fakeUserId", "password")
    private val user = mockk<User>(relaxed = true)

    private val runtimeConfig: TestAirshipRuntimeConfig = TestAirshipRuntimeConfig(
        RemoteConfig(
            RemoteAirshipConfig(
                "https://remote-data",
                "https://device",
                "https://wallet",
                "https://analytics",
                "https://metered-usage"
            )
        )
    )

    private val jobHandler: InboxJobHandler =
        InboxJobHandler(user, dataStore, mockMessageDao, mockInboxApiClient)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun teardown() {
        Dispatchers.resetMain()
        dataStore.tearDown()
    }

    @Test
    @Throws(RequestException::class)
    public fun testUpdateMessagesNotModified(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")

        // Return a 304 response
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_NOT_MODIFIED,
            value = null,
            body = null,
            headers = null
        )

        assertTrue(jobHandler.syncMessageList(userCredentials, "channelId"))

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals("some last modified", dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null))
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testUpdateMessagesEmpty(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"
        val responseHeaders: Map<String, String> = mapOf("Last-Modified" to "some other last modified")

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = responseHeaders
        )

        assertTrue(jobHandler.syncMessageList(userCredentials, "channelId"))

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(
            "some other last modified",
            dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null)
        )
    }

    @Test
    public fun testUpdateMessages(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody =
            "{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," + "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," + "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," + "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," + "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," + "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," + "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        assertTrue(jobHandler.syncMessageList(userCredentials, "channelId"))
    }

    @Test
    public fun testUpdateMessagesServerError(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = JsonList.EMPTY_LIST,
            body = "{ failed }",
            headers = null
        )

        assertFalse(jobHandler.syncMessageList(userCredentials, "channelId"))

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals("some last modified", dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null))
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncDeletedMessageStateServerError(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = emptyMap()
        )

        val idsToDelete = mutableSetOf<String>()
        val messageToDelete = createFakeMessage(messageId = "id1", unread = false, deleted = true)
        val messageToDelete2 = createFakeMessage(messageId = "id2", unread = false, deleted = true)
        val messageCollection = mutableListOf<MessageEntity>()
        val reportingsToDelete: MutableList<JsonValue> = ArrayList()

        messageCollection.add(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete.id,
                    messageToDelete.rawMessageJson
                )
            )
        )

        messageCollection.add(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete2.id,
                    messageToDelete2.rawMessageJson
                )
            )
        )

        for (entity in messageCollection) {
            entity.messageReporting?.let { reportingsToDelete.add(it) }
            idsToDelete.add(entity.messageId)
        }

        coEvery { mockMessageDao.getLocallyDeletedMessages() } returns messageCollection

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.syncDeletedMessageState(userCredentials, "channelId", reportingsToDelete)
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = "{ failed }",
            headers = null
        )

        assertFalse(jobHandler.syncDeletedMessageState(userCredentials, "channelId"))

        coVerify(exactly = 0) { mockMessageDao.deleteMessages(idsToDelete) }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncDeletedMessageStateSucceeds(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        val idsToDelete = mutableSetOf<String>()
        val reportingsToDelete: MutableList<JsonValue> = ArrayList()
        val messageToDelete = createFakeMessage(messageId = "id1", unread = false, deleted = true)
        val messageToDelete2 =  createFakeMessage(messageId = "id2", unread = false, deleted = true)

        val messagesToDelete = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete.id,
                    messageToDelete.rawMessageJson
                )
            ),
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete2.id,
                    messageToDelete2.rawMessageJson
                )
            )
        )

        for (entity in messagesToDelete) {
            entity.messageReporting?.let { reportingsToDelete.add(it) }
            idsToDelete.add(entity.messageId)
        }

        coEvery { mockMessageDao.getLocallyDeletedMessages() } returns messagesToDelete

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.syncDeletedMessageState(userCredentials, "channelId", reportingsToDelete)
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = null,
            headers = null
        )

        assertTrue(jobHandler.syncDeletedMessageState(userCredentials, "channelId"))

        coVerify { mockMessageDao.deleteMessages(idsToDelete) }
    }

    @Test
    public fun testSyncReadMessageStateServerError(): TestResult = runTest {
        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = emptyMap()
        )

        val idsToUpdate = mutableSetOf<String>()
        val reportingsToUpdate: MutableList<JsonValue> = ArrayList()
        val messageToUpdate = createFakeMessage(messageId = "id1", unread = false, deleted = false)
        val messageToUpdate2 = createFakeMessage(messageId = "id2", unread = false, deleted = false)

        val messagesToUpdate = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate.id,
                    messageToUpdate.rawMessageJson
                )
            ),
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate2.id,
                    messageToUpdate2.rawMessageJson
                )
            )
        )

        for (entity in messagesToUpdate) {
            entity.messageReporting?.let { reportingsToUpdate.add(it) }
            idsToUpdate.add(entity.messageId)
        }

        coEvery { mockMessageDao.getLocallyReadMessages() } returns messagesToUpdate

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.syncReadMessageState(userCredentials, "channelId", reportingsToUpdate)
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = "{ failed }",
            headers = null
        )

        assertFalse(jobHandler.syncReadMessageState(userCredentials, "channelId"))

        coVerify(exactly = 0) { mockMessageDao.markMessagesReadOrigin(idsToUpdate) }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncReadMessageStateSucceeds(): TestResult = runTest {
        // Set a valid user
        user.setUser(UserCredentials("fakeUserId", "password"))

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(userCredentials, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        val idsToUpdate = mutableSetOf<String>()
        val reportingsToUpdate: MutableList<JsonValue> = ArrayList()
        val messageToUpdate = createFakeMessage(messageId = "id1", unread = false, deleted = false)

        val messagesToUpdate = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate.id,
                    messageToUpdate.rawMessageJson
                )
            )
        )

        for (entity in messagesToUpdate) {
            entity.messageReporting?.let { reportingsToUpdate.add(it) }
            idsToUpdate.add(entity.messageId)
        }

        coEvery { mockMessageDao.getLocallyReadMessages() } returns messagesToUpdate

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.syncReadMessageState(userCredentials, "channelId", reportingsToUpdate)
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = "{ \"messages\": []}",
            headers = null
        )

        //val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()
        assertTrue(jobHandler.syncReadMessageState(userCredentials, "channelId"))

        coVerify { mockMessageDao.markMessagesReadOrigin(idsToUpdate) }
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    @Throws(RequestException::class)
    public fun testCreateUserWithAmazonChannel(): TestResult = runTest {
        every { user.userCredentials } returns null

        runtimeConfig.setPlatform(Platform.AMAZON)

        val responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }"

        val newCredentials = UserCredentials("someUserId", "someUserToken")

        coEvery {
            mockInboxApiClient.createUser("channelId")
        } returns RequestResult(
            status = HTTP_CREATED,
            value = newCredentials,
            body = responseBody,
            headers = null
        )

        assertEquals(jobHandler.getOrCreateUserCredentials("channelId"), newCredentials)
        verify { user.onCreated(newCredentials, "channelId") }
    }

    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    public fun testCreateUserWithAndroidChannel(): TestResult = runTest {
        every { user.userCredentials } returns null

        runtimeConfig.setPlatform(Platform.ANDROID)

        val responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }"
        val newCredentials = UserCredentials("someUserId", "someUserToken")

        coEvery {
            mockInboxApiClient.createUser("channelId")
        } returns RequestResult(
            status = HTTP_CREATED,
            value = newCredentials,
            body = responseBody,
            headers = null
        )

        assertEquals(jobHandler.getOrCreateUserCredentials("channelId"), newCredentials)
        verify { user.onCreated(newCredentials, "channelId") }
    }

    /**
     * Test create user failed.
     */
    @Test
    public fun testCreateUserFailed(): TestResult = runTest {
        every { user.userCredentials } returns null

        // Set a error response
        coEvery {
            mockInboxApiClient.createUser("channelId")
        } returns RequestResult(RequestException("Failed to create user"))

        assertNull(jobHandler.getOrCreateUserCredentials("channelId"))
    }

    /**
     * Test user update on amazon.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAmazon(): TestResult = runTest {
        every { user.userCredentials } returns userCredentials

        runtimeConfig.setPlatform(Platform.AMAZON)

        // Set a successful response
        coEvery {
            mockInboxApiClient.updateUser(userCredentials, "channelId")
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body ="{ \"ok\" }",
            headers = null
        )

        assertEquals(jobHandler.getOrCreateUserCredentials("channelId"), userCredentials)
        verify { user.onUpdated("channelId") }
    }


    /**
     * Test user update on android.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAndroid(): TestResult = runTest {
        every { user.userCredentials } returns userCredentials

        runtimeConfig.setPlatform(Platform.ANDROID)

        // Set a user
        user.setUser(UserCredentials("someUserId", "someUserToken"))

        // Set a successful response
        coEvery {
            mockInboxApiClient.updateUser(userCredentials, "channelId")
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = "{ \"ok\" }",
            headers = null
        )

        assertEquals(jobHandler.getOrCreateUserCredentials("channelId"), userCredentials)
        verify { user.onUpdated("channelId") }
    }


    /**
     * Test user update failed request.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserRequestFail(): TestResult = runTest {
        every { user.userCredentials } returns userCredentials

        // Set a error response
        coEvery {
            mockInboxApiClient.updateUser(userCredentials, "channelId")
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = null,
            headers = null
        )

        assertNull(jobHandler.getOrCreateUserCredentials("channelId"))
    }

    /**
     * Test user is recreated on unauthorized response from update.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserRequestUnauthorizedRecreatesUser(): TestResult = runTest {
        every { user.userCredentials } returns userCredentials

        val recreatedUserId = "recreatedUserId"
        val recreatedToken = "recreatedToken"
        val channelId = "channelId"

        val expectedCredentials = UserCredentials(username = recreatedUserId, password = recreatedToken)

        // Set error response for user update
        coEvery {
            mockInboxApiClient.updateUser(userCredentials, channelId)
        } returns RequestResult(
            status = HTTP_UNAUTHORIZED,
            value = Unit,
            body = null,
            headers = null
        )

        // Set success response for user create
        val result = UserCredentials(recreatedUserId, recreatedToken)
        val responseBody = String.format(
            "{ \"user_id\": \"%s\", \"password\": \"%s\" }", recreatedUserId, recreatedToken
        )

        coEvery {
            mockInboxApiClient.createUser(channelId)
        } returns RequestResult(
            status = HTTP_CREATED,
            value = result,
            body = responseBody,
            headers = null
        )

        assertEquals(jobHandler.getOrCreateUserCredentials("channelId"), expectedCredentials)

        // Sanity check requests were made as expected
        coVerifyOrder {
            user.userCredentials
            user.registeredChannelId
            mockInboxApiClient.updateUser(userCredentials, channelId)
            mockInboxApiClient.createUser(channelId)
            user.onCreated(expectedCredentials, "channelId")
        }
    }

    @Test
    public fun `test loadAirshipLayout returns layout on success`(): TestResult = runTest {
        val messageJson = JsonValue.parseString("""
            {
              "message_id": "test-message-id",
              "message_body_url": "https://some.url",
              "message_url": "https://some.url",
              "unread": true,
              "message_sent": "2024-10-21 18:41:03",
              "title": "Message title",
              "content_type": "${Message.ContentType.THOMAS.jsonValue}"
            }
        """.trimIndent())
        val message = Message.create(messageJson, isUnreadClient = true, isDeleted = false)!!

        coEvery { user.userCredentials } returns userCredentials
        coEvery {
            mockInboxApiClient.loadAirshipLayout(Uri.parse(message.bodyUrl), userCredentials)
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(defaultLayoutJson),
            body = null,
            headers = null
        )

        val result = jobHandler.loadAirshipLayout(message)
        val expected = AirshipLayout(JsonValue.parseString(defaultLayoutJson))

        assertEquals(expected, result)
        coVerify { mockInboxApiClient.loadAirshipLayout(Uri.parse(message.bodyUrl!!), userCredentials) }
    }

    @Test
    public fun `test loadAirshipLayout returns null for wrong content type`(): TestResult = runTest {
        val messageJson = JsonValue.parseString("""
            {
              "message_id": "test-message-id",
              "message_body_url": "https://some.url",
              "message_url": "https://some.url",
              "unread": true,
              "message_sent": "2024-10-21 18:41:03",
              "title": "Message title",
              "content_type": "${Message.ContentType.HTML.jsonValue}"
            }
        """.trimIndent())
        val message = Message.create(messageJson, isUnreadClient = true, isDeleted = false)!!

        val result = jobHandler.loadAirshipLayout(message)

        assertNull(result)
        coVerify(exactly = 0) { mockInboxApiClient.loadAirshipLayout(any(), any()) }
    }

    @Test
    public fun `test loadAirshipLayout returns null on API failure`(): TestResult = runTest {
        val messageJson = JsonValue.parseString("""
            {
              "message_id": "test-message-id",
              "message_body_url": "https://some.url",
              "message_url": "https://some.url",
              "unread": true,
              "message_sent": "2024-10-21 18:41:03",
              "title": "Message title",
              "content_type": "${Message.ContentType.THOMAS.jsonValue}"
            }
        """.trimIndent())
        val message = Message.create(messageJson, isUnreadClient = true, isDeleted = false)!!

        coEvery { user.userCredentials } returns userCredentials
        coEvery {
            mockInboxApiClient.loadAirshipLayout(Uri.parse(message.bodyUrl!!), userCredentials)
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = null,
            body = null,
            headers = null
        )

        val result = jobHandler.loadAirshipLayout(message)

        assertNull(result)
        coVerify { mockInboxApiClient.loadAirshipLayout(Uri.parse(message.bodyUrl!!), userCredentials) }
    }

    @Test
    public fun `test loadAirshipLayout returns null on exception`(): TestResult = runTest {
        val messageJson = JsonValue.parseString("""
            {
              "message_id": "test-message-id",
              "message_body_url": "https://some.url",
              "message_url": "https://some.url",
              "unread": true,
              "message_sent": "2024-10-21 18:41:03",
              "title": "Message title",
              "content_type": "${Message.ContentType.THOMAS.jsonValue}"
            }
        """.trimIndent())
        val message = Message.create(messageJson, isUnreadClient = true, isDeleted = false)!!

        coEvery { user.userCredentials } returns userCredentials
        coEvery {
            mockInboxApiClient.loadAirshipLayout(any(), any())
        } throws RuntimeException("test exception")

        val result = jobHandler.loadAirshipLayout(message)

        assertNull(result)
        coVerify { mockInboxApiClient.loadAirshipLayout(Uri.parse(message.bodyUrl!!), userCredentials) }
    }

    @Throws(JsonException::class)
    @Suppress("SameParameterValue") // unread is always false
    private fun createFakeMessage(messageId: String, unread: Boolean, deleted: Boolean): Message {
        @Language("JSON")
        val messageJson = JsonValue.parseString("""
            {
              "message_id": "$messageId",
              "message_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/",
              "message_body_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/",
              "message_read_url": "https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/",
              "message_reporting": {
                "message_id": "$messageId"
              },
              "unread": true,
              "message_sent": "2024-10-21 18:41:03",
              "title": "Message title",
              "extra": {
                "some_key": "some_value"
              },
              "content_type": "text/html",
              "content_size": "128"
            }
        """.trimIndent())

        return Message.create(messageJson, unread, deleted)!!
    }

    private val defaultLayoutJson = """
            {
                  "version":1,
                  "presentation":{
                     "type":"embedded",
                     "embedded_id":"home_banner",
                     "default_placement":{
                        "size":{
                           "width":"50%",
                           "height":"50%"
                        }
                     }
                  },
                  "view":{
                     "type":"container",
                     "items":[]
                  }
                }
        """.trimIndent()
}
