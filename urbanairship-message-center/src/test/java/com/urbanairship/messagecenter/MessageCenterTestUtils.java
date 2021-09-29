/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.test.core.app.ApplicationProvider;

public class MessageCenterTestUtils {

    private static List<MessageEntity> messageEntities;
    private static MessageDao messageDao;
    private static MessageDatabase messageDatabase;

    public static void setup() {
        messageDatabase = MessageDatabase.createInMemoryDatabase(ApplicationProvider.getApplicationContext());
        messageDao = messageDatabase.getDao();
    }

    public static void insertMessage(String messageId) {
        insertMessage(messageId,null);
    }

    public static void insertMessage(String messageId, Map<String, String> extras) {
        insertMessage(messageId, extras, false);
    }

    public static void insertMessage(String messageId, Map<String, String> extras, boolean expired) {
        Message message = createMessage(messageId, extras, expired);
        messageDao.insert(MessageEntity.createMessageFromPayload(messageId, message.getRawMessageJson()));
        messageEntities = messageDao.getMessages();
    }

    public static Message createMessage(String messageId, Map<String, String> extras, boolean expired) {
        Map<String, Object> payload = new HashMap<>();
        payload.put(Message.MESSAGE_ID_KEY, messageId);
        payload.put(Message.MESSAGE_REPORTING_KEY, JsonValue.wrap(messageId));
        payload.put(Message.MESSAGE_BODY_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s/body/", messageId));
        payload.put(Message.MESSAGE_READ_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s/read/", messageId));
        payload.put(Message.MESSAGE_URL_KEY, String.format("https://go.urbanairship.com/api/user/tests/messages/%s", messageId));
        payload.put(Message.TITLE_KEY, String.format("%s title", messageId));
        payload.put(Message.UNREAD_KEY, true);
        payload.put(Message.MESSAGE_SENT_KEY, DateUtils.createIso8601TimeStamp(System.currentTimeMillis()));

        if (extras != null) {
            payload.put(Message.EXTRA_KEY, extras);
        }

        if (expired) {
            payload.put(Message.MESSAGE_EXPIRY_KEY, DateUtils.createIso8601TimeStamp(0));
        }

        return Message.create(JsonValue.wrapOpt(payload), true, false);
    }

}
