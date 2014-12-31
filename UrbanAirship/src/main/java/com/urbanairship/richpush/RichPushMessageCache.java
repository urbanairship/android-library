/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.richpush;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple RichPushMessage cache
 */
class RichPushMessageCache {

    private final Map<String, RichPushMessage> unreadMessages = new ConcurrentHashMap<>();
    private final Map<String, RichPushMessage> readMessages = new ConcurrentHashMap<>();

    /**
     * Adds a RichPushMessage to the cache
     *
     * @param message RichPushMessage to cache
     */
    void addMessage(RichPushMessage message) {
        removeMessage(message);
        if (message.isRead()) {
            readMessages.put(message.getMessageId(), message);
        } else {
            unreadMessages.put(message.getMessageId(), message);
        }
    }

    /**
     * Fetches a message from the cache
     *
     * @param messageId id of the message to fetch from the cache
     * @return RichPushMessage if found, null otherwise
     */
    RichPushMessage getMessage(String messageId) {
        if (unreadMessages.containsKey(messageId)) {
            return unreadMessages.get(messageId);
        }
        return readMessages.get(messageId);
    }

    /**
     * Gets the total number of messages
     *
     * @return the number of messages in the cache
     */
    int getMessageCount() {
        return getUnreadMessageCount() + getReadMessageCount();
    }

    /**
     * Gets the number of unread messages
     *
     * @return the number of unread messages in the cache
     */
    int getUnreadMessageCount() {
        return unreadMessages.size();
    }

    /**
     * Gets the number of read messages
     *
     * @return the number of read messages in the cache
     */
    int getReadMessageCount() {
        return readMessages.size();
    }

    /**
     * Gets all the messages in the cache
     *
     * @return A new List of messages in the cache
     */
    List<RichPushMessage> getMessages() {
        List<RichPushMessage> messages = new ArrayList<>();
        messages.addAll(unreadMessages.values());
        messages.addAll(readMessages.values());

        return messages;
    }

    /**
     * Gets all the unread messages in the cache
     *
     * @return A new List of unread messages in the cache
     */
    List<RichPushMessage> getUnreadMessages() {
        return new ArrayList<>(unreadMessages.values());
    }

    /**
     * Gets all the read messages in the cache
     *
     * @return A new List of read messages in the cache
     */
    List<RichPushMessage> getReadMessages() {
        return new ArrayList<>(readMessages.values());
    }

    /**
     * Removes the message from the cache
     *
     * @param message Message to remove from the cache
     */
    void removeMessage(RichPushMessage message) {
        readMessages.remove(message.getMessageId());
        unreadMessages.remove(message.getMessageId());
    }

    Set<String> getMessageIds() {
        Set<String> messageIds = new HashSet<>(getMessageCount());
        messageIds.addAll(readMessages.keySet());
        messageIds.addAll(unreadMessages.keySet());
        return messageIds;
    }
}
