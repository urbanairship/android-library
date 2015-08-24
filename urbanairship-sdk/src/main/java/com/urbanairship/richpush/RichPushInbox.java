/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

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

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The RichPushInbox singleton provides access to the device's local inbox data.
 * Modifications (e.g., deletions or mark read) will be sent to the Urban Airship
 * server the next time the inbox is synchronized.
 *
 * @author Urban Airship
 */
public class RichPushInbox {

    /**
     * Intent action to view the rich push inbox.
     */
    public static final String VIEW_INBOX_INTENT_ACTION = "com.urbanairship.VIEW_RICH_PUSH_INBOX";

    /**
     * Intent action to view a rich push message.
     */
    public static final String VIEW_MESSAGE_INTENT_ACTION = "com.urbanairship.VIEW_RICH_PUSH_MESSAGE";

    /**
     * Scheme used for @{code message:<MESSAGE_ID>} when requesting to view a message with
     * {@code com.urbanairship.VIEW_RICH_PUSH_MESSAGE}.
     */
    public static final String MESSAGE_DATA_SCHEME = "message";

    private static final SentAtRichPushMessageComparator MESSAGE_COMPARATOR = new SentAtRichPushMessageComparator();

    private final static Object inboxLock = new Object();
    private final List<Listener> listeners = new ArrayList<>();

    private final Set<String> deletedMessageIds = new HashSet<>();
    private final Map<String, RichPushMessage> unreadMessages = new HashMap<>();
    private final Map<String, RichPushMessage> readMessages = new HashMap<>();

    private final RichPushResolver richPushResolver;

    final ExecutorService executor = Executors.newSingleThreadExecutor();

    RichPushInbox(Context context) {
        this(new RichPushResolver(context));
    }

    RichPushInbox(RichPushResolver resolver) {
        this.richPushResolver = resolver;
    }

    // API

    /**
     * A listener interface for receiving event callbacks related to inbox database updates.
     */
    public interface Listener {
        void onUpdateInbox();
    }

    /**
     * Subscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushInbox.Listener} interface.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushInbox.Listener} interface.
     */
    public void removeListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Gets the total message count.
     *
     * @return The number of RichPushMessages currently in the inbox.
     */
    public int getCount() {
        synchronized (inboxLock) {
            return unreadMessages.size() + readMessages.size();
        }
    }

    /**
     * Gets all the message ids in the inbox.
     *
     * @return A set of message ids.
     */
    @NonNull
    public Set<String> getMessageIds() {
        synchronized (inboxLock) {
            Set<String> messageIds = new HashSet<>(getCount());
            messageIds.addAll(readMessages.keySet());
            messageIds.addAll(unreadMessages.keySet());
            return messageIds;
        }
    }

    /**
     * Gets the total read message count.
     *
     * @return The number of read RichPushMessages currently in the inbox.
     */
    public int getReadCount() {
        synchronized (inboxLock) {
            return readMessages.size();
        }
    }

    /**
     * Gets the total unread message count.
     *
     * @return The number of unread RichPushMessages currently in the inbox.
     */
    public int getUnreadCount() {
        synchronized (inboxLock) {
            return unreadMessages.size();
        }
    }

    /**
     * Gets a list of RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getMessages() {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>(getCount());
            messages.addAll(unreadMessages.values());
            messages.addAll(readMessages.values());
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of unread RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getUnreadMessages() {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>(unreadMessages.values());
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of read RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getReadMessages() {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>(readMessages.values());
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Get the {@link RichPushMessage} with the corresponding message ID.
     *
     * @param messageId The message ID of the desired {@link RichPushMessage}.
     * @return A {@link RichPushMessage} or <code>null</code> if one does not exist.
     */
    @Nullable
    public RichPushMessage getMessage(String messageId) {
        if (messageId == null) {
            return null;
        }

        synchronized (inboxLock) {
            if (unreadMessages.containsKey(messageId)) {
                return unreadMessages.get(messageId);
            }
            return readMessages.get(messageId);
        }
    }

    // actions

    /**
     * Mark {@link RichPushMessage}s read in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesRead(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesRead(messageIds);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                RichPushMessage message = unreadMessages.get(messageId);

                if (message != null) {
                    message.unreadClient = false;
                    unreadMessages.remove(messageId);
                    readMessages.put(messageId, message);
                }
            }

            notifyListeners();
        }
    }

    /**
     * Mark {@link RichPushMessage}s unread in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesUnread(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesUnread(messageIds);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                RichPushMessage message = readMessages.get(messageId);

                if (message != null) {
                    message.unreadClient = true;
                    readMessages.remove(messageId);
                    unreadMessages.put(messageId, message);
                }
            }
        }

        notifyListeners();
    }

    /**
     * Mark {@link RichPushMessage}s deleted.
     * <p/>
     * Note that in most cases these messages aren't immediately deleted on the server, but they will
     * be inaccessible on the device as soon as they're marked deleted.
     *
     * @param messageIds A set of message ids.
     */
    public void deleteMessages(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesDeleted(messageIds);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                RichPushMessage message = getMessage(messageId);
                if (message != null) {
                    message.deleted = true;
                    unreadMessages.remove(messageId);
                    readMessages.remove(messageId);
                    deletedMessageIds.add(messageId);
                }
            }
        }

        notifyListeners();
    }

    /**
     * Refreshes the inbox messages from the DB.
     */
    void refresh() {
        Cursor inboxCursor = richPushResolver.getAllMessages();

        if (inboxCursor == null) {
            return;
        }

        List<RichPushMessage> messageList = new ArrayList<>(inboxCursor.getCount());

        // Read all the messages from the database
        while (inboxCursor.moveToNext()) {
            RichPushMessage message = messageFromCursor(inboxCursor);
            if (message != null) {
                messageList.add(message);
            }
        }

        // Sync the messages
        synchronized (inboxLock) {

            // Save the unreadMessageIds
            Set<String> previousUnreadMessageIds = new HashSet<>(unreadMessages.keySet());
            Set<String> previousDeletedMessageIds = new HashSet<>(deletedMessageIds);

            // Clear the current messages
            unreadMessages.clear();
            readMessages.clear();

            // Process the new messages
            for (RichPushMessage message : messageList) {

                // Deleted
                if (message.isDeleted() || previousDeletedMessageIds.contains(message.getMessageId())) {
                    deletedMessageIds.add(message.getMessageId());
                    continue;
                }

                // Unread - check the previousUnreadMessageIds if any mark reads are still in process
                if (message.unreadClient || previousUnreadMessageIds.contains(message.getMessageId())) {
                    message.unreadClient = true;
                    unreadMessages.put(message.getMessageId(), message);
                    continue;
                }

                // Read
                readMessages.put(message.getMessageId(), message);
            }
        }

        inboxCursor.close();

        notifyListeners();
    }

    /**
     * A helper method to create a rich push message
     * from a cursor and not worry about any exceptions.
     *
     * @param cursor Cursor pointing to a rich push message.
     * @return RichPushMessage on success, or <code>null</code> if anything went wrong.
     */
    private RichPushMessage messageFromCursor(@NonNull Cursor cursor) {
        try {
            return RichPushMessage.messageFromCursor(cursor);
        } catch (JSONException e) {
            Logger.error("Failed to parse message from the database.", e);
        }
        return null;
    }

    /**
     * Notifies all of the registered listeners that the
     * inbox updated.
     */
    private void notifyListeners() {
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    for (Listener listener : new ArrayList<>(listeners)) {
                        listener.onUpdateInbox();
                    }
                }
            }
        });
    }

    static class SentAtRichPushMessageComparator implements Comparator<RichPushMessage> {
        @Override
        public int compare(RichPushMessage lhs, RichPushMessage rhs) {
            return rhs.getSentDate().compareTo(lhs.getSentDate());
        }
    }
}
