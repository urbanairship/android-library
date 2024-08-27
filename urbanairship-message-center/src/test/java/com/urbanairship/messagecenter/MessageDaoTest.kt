package com.urbanairship.messagecenter;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class MessageDaoTest {
    private final String messageId = "1_message_id";
    private final String messageId2 = "2_message_id";
    private final String messageTitle1 = "Message 1 Title";
    private final String messageTitle2 = "Message 2 Title";
    private JsonValue messageJson;
    private JsonValue messageJson2;

    private static MessageEntity ENTITY;
    private static MessageEntity ENTITY2;

    private MessageDatabase db;
    private MessageDao messageDao;

    @Before
    public void setUp() throws JsonException {
        messageJson = JsonValue.parseString("{\"message_id\": \"" + messageId + "\"," +
                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                "\"message_reporting\": {\n" +
                "                 \"message_id\": \"" + messageId + "\"" +
                "               }," +
                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                "\"title\": \"" + messageTitle1 + "\", \"extra\": { \"some_key\": \"some_value\"}," +
                "\"content_type\": \"text/html\", \"content_size\": \"128\"}");
        messageJson2 = JsonValue.parseString("{\"message_id\": \"" + messageId2 + "\"," +
                "\"message_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/\"," +
                "\"message_body_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/body/\"," +
                "\"message_read_url\": \"https://go.urbanairship.com/api/user/userId/messages/message/some_mesg_id/read/\"," +
                "\"message_reporting\": {\n" +
                "                 \"message_id\": \"" + messageId2 + "\"" +
                "               }," +
                "\"unread\": true, \"message_sent\": \"2010-09-05 12:13 -0000\"," +
                "\"title\": \"" + messageTitle2 + "\", \"extra\": { \"some_key\": \"some_value\"}," +
                "\"content_type\": \"text/html\", \"content_size\": \"128\"}");

        ENTITY = MessageEntity.createMessageFromPayload(messageId, messageJson);
        ENTITY2 = MessageEntity.createMessageFromPayload(messageId2, messageJson2);

        db = MessageDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext());
        messageDao = db.getDao();

        assertEquals(0, messageDao.getMessages().size());
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void testInsert() {
        messageDao.insert(ENTITY);
        assertEquals(1, messageDao.getMessages().size());
    }

    @Test()
    public void testInsertsReplaceOnConflict() {
        // Insert duplicate messages.
        insertMessage("foo", messageJson);
        insertMessage("foo", messageJson);
        assertEquals(1, messageDao.getMessages().size());
        assertEquals(messageTitle1, messageDao.getMessages().get(0).title);

        // Insert another with same ID, but different data.
        insertMessage("foo", messageJson2);
        assertEquals(1, messageDao.getMessages().size());
        assertEquals(messageTitle2, messageDao.getMessages().get(0).title);
    }

    @Test
    public void testGetMessages() {
        messageDao.insert(ENTITY);
        messageDao.insert(ENTITY2);
        assertEquals(2, messageDao.getMessages().size());
        List<MessageEntity> messageEntities = messageDao.getMessages();

        assertEquals(messageId, messageEntities.get(0).messageId);
        assertEquals(messageId2, messageEntities.get(1).messageId);
    }

    @Test
    public void testGetReadMessages() {
        messageDao.insert(ENTITY);
        messageDao.insert(ENTITY2);
        assertEquals(2, messageDao.getMessages().size());
        List<String> messagesIds = new ArrayList<>();
        messagesIds.add(messageId);
        messageDao.markMessagesRead(messagesIds);

        List<MessageEntity> messageEntities = messageDao.getLocallyReadMessages();
        assertEquals(1, messageEntities.size());
        assertEquals(messageId, messageEntities.get(0).messageId);
    }

    @Test
    public void testGetDeletedMessages() {
        messageDao.insert(ENTITY);
        messageDao.insert(ENTITY2);
        assertEquals(2, messageDao.getMessages().size());
        List<String> messagesIds = new ArrayList<>();
        messagesIds.add(messageId);
        messageDao.markMessagesDeleted(messagesIds);

        List<MessageEntity> messageEntities = messageDao.getLocallyDeletedMessages();
        assertEquals(1, messageEntities.size());
        assertEquals(messageId, messageEntities.get(0).messageId);
    }

    @Test
    public void testDeleteTooManyMessages() {
        List<String> messageIds = insertMessages(2000);
        assertEquals(2000, messageDao.getMessages().size());

        messageDao.deleteMessages(messageIds);
        assertEquals(0, messageDao.getMessages().size());
    }

    @SuppressWarnings("SameParameterValue")
    private List<String> insertMessages(int count) {
        List<String> messageIds = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            messageIds.add(insertMessage(UUID.randomUUID().toString(), messageJson));
        }

        return messageIds;
    }

    private String insertMessage(String id, JsonValue json) {
        MessageEntity message = MessageEntity.createMessageFromPayload(id, json);
        assert message != null;
        messageDao.insert(message);
        return message.getMessageId();
    }
}
