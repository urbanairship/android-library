
package com.urbanairship.chat.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.TestAirshipRuntimeConfig
import com.urbanairship.chat.ChatDirection
import com.urbanairship.chat.ChatRouting
import com.urbanairship.chat.websocket.WebSocket
import com.urbanairship.chat.websocket.WebSocketFactory
import com.urbanairship.chat.websocket.WebSocketListener
import com.urbanairship.config.AirshipUrlConfig
import com.urbanairship.util.DateUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ChatConnectionTest {

    internal class TestSocketFactory(var socket: WebSocket) : WebSocketFactory {

        var lastListener: WebSocketListener? = null
        var lastUrl: String? = null

        override fun create(url: String, listener: WebSocketListener): WebSocket {
            lastUrl = url
            lastListener = listener
            return socket
        }
    }

    private lateinit var mockWebSocket: WebSocket
    private lateinit var mockChatConnectionListener: ChatConnection.ChatListener
    private lateinit var socketFactory: TestSocketFactory
    private lateinit var chatConnection: ChatConnection
    private lateinit var runtimeConfig: TestAirshipRuntimeConfig
    private lateinit var testScope: TestCoroutineScope

    @Before
    fun setUp() {
        testScope = TestCoroutineScope()
        runtimeConfig = TestAirshipRuntimeConfig.newTestConfig()
        runtimeConfig.urlConfig = AirshipUrlConfig.newBuilder().setChatSocketUrl("wss://test.urbanairship.com").build()

        mockWebSocket = mock()
        mockChatConnectionListener = mock()
        socketFactory = TestSocketFactory(mockWebSocket)
        chatConnection = ChatConnection(runtimeConfig, socketFactory, testScope)
        chatConnection.chatListener = mockChatConnectionListener
    }

    @After
    fun cleanup() {
        chatConnection.close()
        testScope.cleanupTestCoroutines()
    }

    @Test
    fun testClosedOnInit() {
        Assert.assertFalse(chatConnection.isOpenOrOpening)
    }

    @Test
    fun testOpen() {
        chatConnection.open("some-uvp")
        verify(mockWebSocket).open()
        Assert.assertEquals("wss://test.urbanairship.com?uvp=some-uvp", socketFactory.lastUrl)
        Assert.assertNotNull(socketFactory.lastListener)
    }

    @Test
    fun testOpenOrIsOpening() {
        chatConnection.open("some-uvp")
        verify(mockWebSocket).open()
        Assert.assertTrue(chatConnection.isOpenOrOpening)
    }

    @Test
    fun testClose() {
        chatConnection.open("some-uvp")
        chatConnection.close()

        verify(mockWebSocket).close()
        verify(mockChatConnectionListener).onClose(ChatConnection.CloseReason.Manual)
    }

    @Test
    fun testRequestConversation() {
        // Prevent heartbeats
        testScope.pauseDispatcher()

        chatConnection.open("some-uvp")
        chatConnection.fetchConversation()

        val expected = ChatRequest.FetchConversation("some-uvp")

        verify(mockWebSocket).send(argThat {
            val parsed = ChatRequest.FetchConversation.parse(this)
            expected == parsed
        })
    }

    @Test
    fun testSendMessage() {
        // Prevent heartbeats
        testScope.pauseDispatcher()

        val date = DateUtils.parseIso8601("2021-01-01T00:00:00")

        chatConnection.open("some-uvp")
        chatConnection.sendMessage("hi", "some attachment", "request id", ChatDirection.OUTGOING, date, ChatRouting("agent!"))

        val expected = ChatRequest.SendMessage("some-uvp", "hi", "some attachment", "request id", ChatDirection.OUTGOING, date, ChatRouting("agent!"))

        verify(mockWebSocket).send(argThat {
            val parsed = ChatRequest.SendMessage.parse(this)
            expected == parsed
        })
    }

    @Test
    fun testSendNilOptionalValues() {
        // Prevent heartbeats
        testScope.pauseDispatcher()

        chatConnection.open("some-uvp")
        chatConnection.sendMessage("hi", null, "request id", ChatDirection.OUTGOING, null, null)

        val expected = ChatRequest.SendMessage("some-uvp", "hi", null, "request id", ChatDirection.OUTGOING, null, ChatRouting(null))

        verify(mockWebSocket).send(argThat {
            val parsed = ChatRequest.SendMessage.parse(this)
            expected == parsed
        })
    }

    @Test
    fun testOnNewMessage() {
        chatConnection.open("some-uvp")

        val response = """
        {
            "type": "new_message",
            "payload":{
                "success":true,
                "message":{
                    "message_id":1617819415247,
                    "created_on":"2021-04-07T18:16:55Z",
                    "direction":0,
                    "text":"Sup",
                    "attachment":null,
                    "request_id":"D9DD85A9-F5A1-4E56-9060-4DB4462CFF32"
                }
            }
        }
        """

        val expected = ChatResponse.NewMessage.parse(response)
        socketFactory.lastListener?.onReceive(response)
        verify(mockChatConnectionListener).onChatResponse(expected)
    }

    @Test
    fun testReceivedMessageResponse() {
        chatConnection.open("some-uvp")

        val response = """
        {
            "type": "message_received",
            "payload":{
                "success":true,
                "message":{
                    "message_id":1617819415247,
                    "created_on":"2021-04-07T18:16:55Z",
                    "direction":0,
                    "text":"Sup",
                    "attachment":null,
                    "request_id":"D9DD85A9-F5A1-4E56-9060-4DB4462CFF32"
                }
            }
        }
        """

        val expected = ChatResponse.MessageReceived.parse(response)
        socketFactory.lastListener?.onReceive(response)
        verify(mockChatConnectionListener).onChatResponse(expected)
    }

    @Test
    fun testConversationResponse() {
        chatConnection.open("some-uvp")

        val response = """
        {
            "type":"conversation_loaded",
            "payload":{
                "messages":[
                {
                "message_id":1617642327507,
                "created_on":"2021-04-05T17:05:27Z",
                "direction":0,
                "text":"Hello",
                "attachment":null,
                "request_id":"D9DD85A9-F5A1-4E56-9060-4DB4462CFF32"
                },
                {
                "message_id":1617642338659,
                "created_on":"2021-04-05T17:05:38Z",
                "direction":1,
                "text":"Hi!",
                "attachment":null,
                "request_id":null
                }
                ]
            }
        }
        """

        val expected = ChatResponse.ConversationLoaded.parse(response)
        socketFactory.lastListener?.onReceive(response)
        verify(mockChatConnectionListener).onChatResponse(expected)
    }

    @Test
    fun testOnOpen() {
        chatConnection.open("some-uvp")

        socketFactory.lastListener?.onOpen()
        verify(mockChatConnectionListener).onOpen()
    }

    @Test
    fun testOnClose() {
        chatConnection.open("some-uvp")

        socketFactory.lastListener?.onClose(1, "some reason")
        verify(mockChatConnectionListener).onClose(ChatConnection.CloseReason.Server(1, "some reason"))
    }

    @Test
    fun testOnError() {
        chatConnection.open("some-uvp")

        val exception = Exception()
        socketFactory.lastListener?.onError(exception)
        verify(mockWebSocket).close()
        verify(mockChatConnectionListener).onClose(ChatConnection.CloseReason.Error(exception))
    }

    @Test
    fun testHeartbeat() {
        chatConnection.open("some-uvp")
        val heartbeat = ChatRequest.Heartbeat("some-uvp")

        testScope.advanceTimeBy(120000)
        verify(mockWebSocket, times(3)).send(argThat {
            val parsed = ChatRequest.Heartbeat.parse(this)
            heartbeat == parsed
        })
    }

    @Test
    fun testHeartbeatStopsOnClose() {
        chatConnection.open("some-uvp")
        chatConnection.close()
        testScope.advanceTimeBy(120000)

        val heartbeat = ChatRequest.Heartbeat("some-uvp")
        verify(mockWebSocket, times(1)).send(argThat {
            val parsed = ChatRequest.Heartbeat.parse(this)
            heartbeat == parsed
        })
    }
}
