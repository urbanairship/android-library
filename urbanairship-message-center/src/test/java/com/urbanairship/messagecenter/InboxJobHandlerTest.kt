/* Copyright Airship and Contributors */
package com.urbanairship.messagecenter

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.PreferenceDataStore
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.UAirship
import com.urbanairship.channel.AirshipChannel
import com.urbanairship.http.RequestException
import com.urbanairship.http.RequestResult
import com.urbanairship.http.RequestSession
import com.urbanairship.http.Response
import com.urbanairship.job.JobInfo
import com.urbanairship.job.JobResult
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonValue
import com.urbanairship.messagecenter.InboxJobHandler.Companion.ACTION_RICH_PUSH_MESSAGES_UPDATE
import com.urbanairship.messagecenter.InboxJobHandler.Companion.ACTION_RICH_PUSH_USER_UPDATE
import com.urbanairship.messagecenter.InboxJobHandler.Companion.LAST_MESSAGE_REFRESH_TIME
import com.urbanairship.remoteconfig.RemoteAirshipConfig
import com.urbanairship.remoteconfig.RemoteConfig
import java.net.HttpURLConnection
import java.net.HttpURLConnection.HTTP_CREATED
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_NOT_MODIFIED
import java.net.HttpURLConnection.HTTP_OK
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.intellij.lang.annotations.Language
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
public class InboxJobHandlerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val testDispatcher = StandardTestDispatcher()
    private val mainLooper: ShadowLooper = Shadows.shadowOf(Looper.getMainLooper());

    private val dataStore: PreferenceDataStore = PreferenceDataStore.inMemoryStore(context)

    private val mockChannel = mockk<AirshipChannel>()
    private val mockMessageDao = mockk<MessageDao>(relaxUnitFun = true) {
        coEvery { getMessageIds() } returns mutableListOf()
        coEvery { messageExists(any()) } returns false
        coEvery { getLocallyReadMessages() } returns emptyList()
        coEvery { getLocallyDeletedMessages() } returns emptyList()
    }
    private val mockInboxApiClient = mockk<InboxApiClient>()

    private val userListener: TestUserListener = TestUserListener()
    private val user: User = User(dataStore, mockChannel)

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

    private val inbox = mockk<Inbox>(relaxUnitFun = true) {
        every { this@mockk.user } returns this@InboxJobHandlerTest.user
    }

    private val jobHandler: InboxJobHandler =
        InboxJobHandler(inbox, user, mockChannel, dataStore, mockMessageDao, mockInboxApiClient)

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Clear any user or password
        user.setUser(null, null)

        // Register the user listener
        user.addListener(userListener)
    }

    @After
    public fun teardown() {
        Dispatchers.resetMain()
    }

    /**
     * Test when user has not been created returns an error code.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUserNotCreated(): TestResult = runTest {
        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify {
            // Verify result receiver
            inbox.onUpdateMessagesFinished(false)
            // Verify no requests were made
            mockInboxApiClient.createUser(any()) wasNot Called

        }
    }

    /**
     * Test updateMessages returns success code when response is HTTP_NOT_MODIFIED.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateMessagesNotModified(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")

        // Return a 304 response
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_NOT_MODIFIED,
            value = null,
            body = null,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals("some last modified", dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null))

        // Verify result receiver
        coVerify { inbox.onUpdateMessagesFinished(true) }
    }

    /**
     * Test that the inbox is updated when the response doesn't contain any messages.
     */
    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testUpdateMessagesEmpty(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"
        val responseHeaders: Map<String, String> = mapOf("Last-Modified" to "some other last modified")

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = responseHeaders
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        // Verify LAST_MESSAGE_REFRESH_TIME was updated
        assertEquals(
            "some other last modified",
            dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null)
        )

        coVerify {
            // Verify result receiver
            inbox.onUpdateMessagesFinished(true)
            // Verify we updated the inbox
            inbox.refresh(true)
        }
    }

    /**
     * Test updateMessages returns success code when response is HTTP_OK.
     */
    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testUpdateMessages(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody =
            "{ \"messages\": [ {\"message_id\": \"some_mesg_id\"," + "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," + "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," + "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," + "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," + "\"title\": \"Message title\", \"extra\": { \"some_key\": \"some_value\"}," + "\"content_type\": \"text/html\", \"content_size\": \"128\"}]}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify {
            // Verify result receiver
            inbox.onUpdateMessagesFinished(true)
            // Verify we updated the inbox
            inbox.refresh(true)
        }
    }

    /**
     * Test updateMessages returns error code when response is HTTP_INTERNAL_ERROR
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateMessagesServerError(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = JsonList.EMPTY_LIST,
            body = "{ failed }",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()
        val result = jobHandler.performJob(jobInfo)

        mainLooper.runToEndOfTasks()
        advanceUntilIdle()

        assertEquals(JobResult.SUCCESS, result)

        // Verify LAST_MESSAGE_REFRESH_TIME was not updated
        assertEquals("some last modified", dataStore.getString(LAST_MESSAGE_REFRESH_TIME, null))

        coVerify {
            // Verify result receiver
            inbox.onUpdateMessagesFinished(false)
            // Verify we updated the inbox
            inbox.refresh(true)
        }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncDeletedMessageStateServerError(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = emptyMap()
        )

        val idsToDelete = ArrayList<String>()
        val messageToDelete = createFakeMessage(messageId = "id1", unread = false, deleted = true)
        val messageToDelete2 = createFakeMessage(messageId = "id2", unread = false, deleted = true)
        val messageCollection = mutableListOf<MessageEntity>()
        val reportingsToDelete: MutableList<JsonValue> = ArrayList()

        messageCollection.add(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete!!.messageId,
                    messageToDelete.rawMessageJson
                )
            )
        )

        messageCollection.add(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete2!!.messageId,
                    messageToDelete2.rawMessageJson
                )
            )
        )

        for (message in messageCollection) {
            message.messageReporting?.let { reportingsToDelete.add(it) }
            idsToDelete.add(message.getMessageId())
        }

        coEvery { mockMessageDao.getLocallyDeletedMessages() } returns messageCollection

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.syncDeletedMessageState(user, "channelId", reportingsToDelete)
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = "{ failed }",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify(exactly = 0) { mockMessageDao.deleteMessages(idsToDelete) }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncDeletedMessageStateSucceeds(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        val idsToDelete = ArrayList<String>()
        val reportingsToDelete: MutableList<JsonValue> = ArrayList()
        val messageToDelete = requireNotNull(
            createFakeMessage(messageId = "id1", unread = false, deleted = true)
        )
        val messageToDelete2 = requireNotNull(
            createFakeMessage(messageId = "id2", unread = false, deleted = true)
        )
        val messagesToDelete = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete.messageId,
                    messageToDelete.rawMessageJson
                )
            ),
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToDelete2.messageId,
                    messageToDelete2.rawMessageJson
                )
            )
        )

        for (message in messagesToDelete) {
            message.messageReporting?.let { reportingsToDelete.add(it) }
            idsToDelete.add(message.getMessageId())
        }

        coEvery { mockMessageDao.getLocallyDeletedMessages() } returns messagesToDelete

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.syncDeletedMessageState(user, "channelId", reportingsToDelete)
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = null,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify { mockMessageDao.deleteMessages(idsToDelete) }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncReadMessageStateServerError(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = emptyMap()
        )

        val idsToUpdate = ArrayList<String>()
        val reportingsToUpdate: MutableList<JsonValue> = ArrayList()
        val messageToUpdate = requireNotNull(
            createFakeMessage(messageId = "id1", unread = false, deleted = false)
        )
        val messageToUpdate2 = requireNotNull(
            createFakeMessage(messageId = "id2", unread = false, deleted = false)
        )
        val messagesToUpdate = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate.messageId,
                    messageToUpdate.rawMessageJson
                )
            ),
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate2.messageId,
                    messageToUpdate2.rawMessageJson
                )
            )
        )

        for (message in messagesToUpdate) {
            message.messageReporting?.let { reportingsToUpdate.add(it) }
            idsToUpdate.add(message.getMessageId())
        }

        coEvery { mockMessageDao.getLocallyReadMessages() } returns messagesToUpdate

        // Return a 500 internal server error
        coEvery {
            mockInboxApiClient.syncReadMessageState(user, "channelId", reportingsToUpdate)
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = "{ failed }",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify(exactly = 0) { mockMessageDao.markMessagesReadOrigin(idsToUpdate) }
    }

    @Test
    @Throws(RequestException::class, JsonException::class)
    public fun testSyncReadMessageStateSucceeds(): TestResult = runTest {
        // Set a valid user
        user.setUser("fakeUserId", "password")

        // Set a channel ID
        every { mockChannel.id } returns "channelId"

        // Set the last refresh time
        dataStore.put(LAST_MESSAGE_REFRESH_TIME, "some last modified")
        val responseBody = "{ \"messages\": []}"

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.fetchMessages(user, "channelId", "some last modified")
        } returns RequestResult(
            status = HTTP_OK,
            value = JsonValue.parseString(responseBody).optMap().opt("messages").requireList(),
            body = responseBody,
            headers = null
        )

        val idsToUpdate = ArrayList<String>()
        val reportingsToUpdate: MutableList<JsonValue> = ArrayList()
        val messageToUpdate =  requireNotNull(
            createFakeMessage(messageId = "id1", unread = false, deleted = false)
        )
        val messagesToUpdate = listOf(
            requireNotNull(
                MessageEntity.createMessageFromPayload(
                    messageToUpdate.messageId,
                    messageToUpdate.rawMessageJson
                )
            )
        )

        for (message in messagesToUpdate) {
            message.messageReporting?.let { reportingsToUpdate.add(it) }
            idsToUpdate.add(message.getMessageId())
        }

        coEvery { mockMessageDao.getLocallyReadMessages() } returns messagesToUpdate

        // Return a 200 message list response with messages
        coEvery {
            mockInboxApiClient.syncReadMessageState(user, "channelId", reportingsToUpdate)
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = "{ \"messages\": []}",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_MESSAGES_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))

        coVerify { mockMessageDao.markMessagesReadOrigin(idsToUpdate) }
    }

    /**
     * Test create user when PushManager has a amazon channel.
     */
    @Test
    @Throws(RequestException::class)
    public fun testCreateUserWithAmazonChannel(): TestResult = runTest {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM)

        every { mockChannel.id } returns "channelId"

        val responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }"

        coEvery {
            mockInboxApiClient.createUser("channelId")
        } returns RequestResult(
            status = HTTP_CREATED,
            value = UserCredentials("someUserId", "someUserToken"),
            body = responseBody,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertTrue(userListener.lastUpdateUserResult == true)

        // Verify user name and user token was set
        assertEquals("someUserId", user.id)
        assertEquals("someUserToken", user.password)
    }

    /**
     * Test create user when PushManager has a android channel.
     */
    @Test
    @Throws(RequestException::class)
    public fun testCreateUserWithAndroidChannel(): TestResult = runTest {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM)
        every { mockChannel.id } returns "channelId"

        val responseBody = "{ \"user_id\": \"someUserId\", \"password\": \"someUserToken\" }"

        coEvery {
            mockInboxApiClient.createUser("channelId")
        } returns RequestResult(
            status = HTTP_CREATED,
            value = UserCredentials("someUserId", "someUserToken"),
            body = responseBody,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertTrue(userListener.lastUpdateUserResult == true)

        // Verify user name and user token was set
        assertEquals("someUserId", user.id)
        assertEquals("someUserToken", user.password)
    }

    /**
     * Test create user when PushManager when a channel has not been created.
     */
    @Test
    public fun testCreateUserNoChannel() {
        every { mockChannel.id } returns null

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertFalse(userListener.lastUpdateUserResult == true)

        // Verify we did not create the user
        assertNull(user.id)
        assertNull(user.password)
    }

    /**
     * Test create user failed.
     */
    @Test
    @Throws(RequestException::class)
    public fun testCreateUserFailed(): TestResult = runTest {
        every { mockChannel.id } returns "channelId"

        // Set a error response
        coEvery {
            mockInboxApiClient.createUser("channelId")
        } throws RequestException("Failed to create user")

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertFalse(userListener.lastUpdateUserResult == true)

        // Verify we did not create the user
        assertNull(user.id)
        assertNull(user.password)
    }

    /**
     * Test user update on amazon.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAmazon(): TestResult = runTest {
        runtimeConfig.setPlatform(UAirship.AMAZON_PLATFORM)

        every { mockChannel.id } returns "channelId"

        // Set a user
        user.setUser("someUserId", "someUserToken")

        // Set a successful response
        coEvery {
            mockInboxApiClient.updateUser(user, "channelId")
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body ="{ \"ok\" }",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        advanceUntilIdle()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        advanceUntilIdle()
        assertEquals(true, userListener.lastUpdateUserResult)
    }

    /**
     * Test user update on android.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserAndroid(): TestResult = runTest {
        runtimeConfig.setPlatform(UAirship.ANDROID_PLATFORM)
        every { mockChannel.id } returns "channelId"

        // Set a user
        user.setUser("someUserId", "someUserToken")

        // Set a successful response
        coEvery {
            mockInboxApiClient.updateUser(user, "channelId")
        } returns RequestResult(
            status = HTTP_OK,
            value = Unit,
            body = "{ \"ok\" }",
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertTrue(userListener.lastUpdateUserResult == true)
    }

    /**
     * Test user update without a channel should not update.
     */
    @Test
    public fun testUpdateUserNoChannel() {
        // Set a user
        user.setUser("someUserId", "someUserToken")

        // Return a null channel
        every { mockChannel.id } returns null

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertFalse(userListener.lastUpdateUserResult == true)
    }

    /**
     * Test user update failed request.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserRequestFail(): TestResult = runTest {
        // Set a user
        user.setUser("someUserId", "someUserToken")
        every { mockChannel.id } returns "channelId"

        // Set a error response
        coEvery {
            mockInboxApiClient.updateUser(user, "channelId")
        } returns RequestResult(
            status = HTTP_INTERNAL_ERROR,
            value = Unit,
            body = null,
            headers = null
        )

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()
        val result = jobHandler.performJob(jobInfo)

        mainLooper.runToEndOfTasks()
        advanceUntilIdle()

        assertEquals(JobResult.SUCCESS, result)
        assertEquals(false, userListener.lastUpdateUserResult)
    }

    /**
     * Test user is recreated on unauthorized response from update.
     */
    @Test
    @Throws(RequestException::class)
    public fun testUpdateUserRequestUnauthorizedRecreatesUser(): TestResult = runTest {
        val unauthorizedUserId = "unauthorizedUserId"
        val unauthorizedToken = "unauthorizedToken"
        val recreatedUserId = "recreatedUserId"
        val recreatedToken = "recreatedToken"
        val channelId = "channelId"

        // Set a user
        user.setUser(unauthorizedUserId, unauthorizedToken)

        every { mockChannel.id } returns channelId

        // Set error response for user update
        coEvery {
            mockInboxApiClient.updateUser(user, channelId)
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

        val jobInfo = JobInfo.newBuilder().setAction(ACTION_RICH_PUSH_USER_UPDATE).build()

        assertEquals(JobResult.SUCCESS, jobHandler.performJob(jobInfo))
        assertTrue(userListener.lastUpdateUserResult == true)

        // Verify user name and user token was set
        assertEquals(recreatedUserId, user.id)
        assertEquals(recreatedToken, user.password)

        // Sanity check requests were made as expected
        coVerifyOrder {
            mockInboxApiClient.updateUser(user, channelId)
            mockInboxApiClient.createUser(channelId)
        }
    }

    @Throws(JsonException::class)
    @Suppress("SameParameterValue") // unread is always false
    private fun createFakeMessage(messageId: String, unread: Boolean, deleted: Boolean): Message? {
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
              "message_sent": "2010-09-05 12:13 -0000",
              "title": "Message title",
              "extra": {
                "some_key": "some_value"
              },
              "content_type": "text/html",
              "content_size": "128"
            }
        """.trimIndent())

        return Message.create(messageJson, unread, deleted)
    }

    /**
     * Listener that captures the last update user result
     */
    private class TestUserListener : User.Listener {

        var lastUpdateUserResult: Boolean? = null

        override fun onUserUpdated(success: Boolean) {
            lastUpdateUserResult = success
        }
    }
}
