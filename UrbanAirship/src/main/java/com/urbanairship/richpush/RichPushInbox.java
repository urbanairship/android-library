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

import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import com.urbanairship.Logger;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
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

    private static final SentAtRichPushMessageComparator richPushMessageComparator = new SentAtRichPushMessageComparator();
    private final List<String> pendingDeletionMessageIds = new ArrayList<>();


    private final List<Listener> listeners = new ArrayList<>();
    private final RichPushMessageCache messageCache = new RichPushMessageCache();
    private RichPushResolver richPushResolver;

    ExecutorService executor = Executors.newSingleThreadExecutor();

    RichPushInbox(Context context) {
        this(new RichPushResolver(context));
    }

    RichPushInbox(RichPushResolver resolver) {
        richPushResolver = resolver;
        updateCacheFromDB();
    }

    // API
    /**
     * A listener interface for receiving event callbacks related to inbox database updates.
     */
    public interface Listener {
        public void onUpdateInbox();
    }

    /**
     * Subscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushInbox.Listener} interface.
     */
    public void addListener(Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushInbox.Listener} interface.
     */
    public void removeListener(Listener listener) {
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
        return messageCache.getMessageCount();
    }

    /**
     * Gets all the message ids in the inbox.
     *
     * @return A set of message ids.
     */
    public Set<String> getMessageIds() {
        return messageCache.getMessageIds();
    }

    /**
     * Gets the total read message count.
     *
     * @return The number of read RichPushMessages currently in the inbox.
     */
    public int getReadCount() {
        return messageCache.getReadMessageCount();
    }

    /**
     * Gets the total unread message count.
     *
     * @return The number of unread RichPushMessages currently in the inbox.
     */
    public int getUnreadCount() {
        return messageCache.getUnreadMessageCount();
    }

    /**
     * Gets a list of RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    public List<RichPushMessage> getMessages() {
        List<RichPushMessage> messages = messageCache.getMessages();
        Collections.sort(messages, richPushMessageComparator);
        return messages;
    }

    /**
     * Gets a list of unread RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    public List<RichPushMessage> getUnreadMessages() {
        List<RichPushMessage> messages = messageCache.getUnreadMessages();
        Collections.sort(messages, richPushMessageComparator);
        return messages;
    }

    /**
     * Gets a list of read RichPushMessages. Sorted by descending sent at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    public List<RichPushMessage> getReadMessages() {
        List<RichPushMessage> messages = messageCache.getReadMessages();
        Collections.sort(messages, richPushMessageComparator);
        return messages;
    }

    /**
     * Get the {@link RichPushMessage} with the corresponding message ID.
     *
     * @param messageId The message ID of the desired {@link RichPushMessage}.
     * @return A {@link RichPushMessage} or <code>null</code> if one does not exist.
     */
    public RichPushMessage getMessage(String messageId) {
        if (messageId == null) {
            return null;
        }
        return messageCache.getMessage(messageId);
    }

    // actions

    /**
     * Mark {@link RichPushMessage}s read in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesRead(final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesRead(messageIds);
            }
        });

        synchronized (messageCache) {
            for (String messageId : messageIds) {
                RichPushMessage message = messageCache.getMessage(messageId);
                if (message != null) {
                    message.unreadClient = false;
                    messageCache.addMessage(message);
                }
            }
        }

        notifyListeners();
    }

    /**
     * Mark {@link RichPushMessage}s unread in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesUnread(final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesUnread(messageIds);
            }
        });

        synchronized (messageCache) {
            for (String messageId : messageIds) {
                RichPushMessage message = messageCache.getMessage(messageId);
                if (message != null) {
                    message.unreadClient = true;
                    messageCache.addMessage(message);
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
    public void deleteMessages(final Set<String> messageIds) {
        pendingDeletionMessageIds.addAll(messageIds);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                richPushResolver.markMessagesDeleted(messageIds);
                pendingDeletionMessageIds.removeAll(messageIds);
            }
        });

        synchronized (messageCache) {
            for (String messageId : messageIds) {
                RichPushMessage message = messageCache.getMessage(messageId);
                if (message != null) {
                    message.deleted = true;
                    messageCache.removeMessage(message);
                }
            }
        }

        notifyListeners();
    }

    /**
     * Updates the cache.
     */
    void updateCache() {
        updateCacheFromDB();
        notifyListeners();
    }

    /**
     * Updates the richMessageCache from the database.
     */
    private void updateCacheFromDB() {
        List<String> deletedIds = new ArrayList<>(pendingDeletionMessageIds);
        Cursor inboxCursor = richPushResolver.getAllMessages();

        if (inboxCursor == null) {
            return;
        }

        while (inboxCursor.moveToNext()) {
            RichPushMessage message = messageFromCursor(inboxCursor);
            if (message == null) {
                continue;
            }

            synchronized (messageCache) {
                // If the message is deleted or expired remove it from the cache
                if (message.isDeleted() || message.isExpired()) {
                    messageCache.removeMessage(message);
                    continue;
                }

                if (deletedIds.contains(message.getMessageId())) {
                    continue;
                }

                RichPushMessage oldCachedMessage = messageCache.getMessage(message.getMessageId());

                // Not currently in the cache
                if (oldCachedMessage == null) {
                    messageCache.addMessage(message);
                    continue;
                }

                // Replace the message with the new one from the db
                messageCache.removeMessage(oldCachedMessage);
                message.unreadClient = oldCachedMessage.unreadClient;
                messageCache.addMessage(message);
            }
        }

        inboxCursor.close();
    }

    /**
     * A helper method to create a rich push message
     * from a cursor and not worry about any exceptions.
     *
     * @param cursor Cursor pointing to a rich push message.
     * @return RichPushMessage on success, or <code>null</code> if anything went wrong.
     */
    private RichPushMessage messageFromCursor(Cursor cursor) {
        try {
            return RichPushMessage.messageFromCursor(cursor);
        } catch (JSONException e) {
            Logger.error(e);
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
                    for (Listener listener : listeners) {
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
