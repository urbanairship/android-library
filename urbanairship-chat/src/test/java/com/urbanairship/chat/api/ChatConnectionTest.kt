package com.urbanairship.chat.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.chat.websocket.WebSocket
import com.urbanairship.chat.websocket.WebSocketFactory
import com.urbanairship.chat.websocket.WebSocketListener
import java.lang.Exception
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

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

    @Before
    fun setUp() {
        mockWebSocket = mock()
        mockChatConnectionListener = mock()
        socketFactory = TestSocketFactory(mockWebSocket)
        chatConnection = ChatConnection(socketFactory)
        chatConnection.chatListener = mockChatConnectionListener
    }

    @Test
    fun testClosedOnInit() {
        Assert.assertFalse(chatConnection.isOpenOrOpening)
    }

    @Test
    fun testOpen() {
        chatConnection.open("some-uvp")
        verify(mockWebSocket).open()
        Assert.assertEquals("wss://rb2socketscontactstest.replybuy.net?uvp=some-uvp", socketFactory.lastUrl)
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
        chatConnection.open("some-uvp")
        chatConnection.fetchConversation()

        val expected = ChatRequest.FetchConversation("some-uvp")

        verify(mockWebSocket).send(argThat {
            val parsed = parse<ChatRequest.FetchConversation>(this)
            expected == parsed
        })
    }

    @Test
    fun testSendMessage() {
        chatConnection.open("some-uvp")
        chatConnection.sendMessage("hi", "some attachment", "request id")

        val expected = ChatRequest.SendMessage("some-uvp", "hi", "some attachment", "request id")

        verify(mockWebSocket).send(argThat {
            val parsed = parse<ChatRequest.SendMessage>(this)
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

        val expected = parse<ChatResponse.NewMessage>(response)
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

        val expected = parse<ChatResponse.MessageReceived>(response)
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

        val expected = parse<ChatResponse.ConversationLoaded>(response)
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

    private inline fun <reified T> parse(string: String): T {

        val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        return jsonParser.decodeFromString(string)
    }
}
