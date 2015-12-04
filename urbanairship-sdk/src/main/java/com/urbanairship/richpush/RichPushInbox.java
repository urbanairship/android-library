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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Cancelable;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;


/**
 * The RichPushInbox singleton provides access to the device's local inbox data.
 * Modifications (e.g., deletions or mark read) will be sent to the Urban Airship
 * server the next time the inbox is synchronized.
 */
public class RichPushInbox extends AirshipComponent {

    /**
     * A listener interface for receiving event callbacks related to inbox updates.
     */
    public interface Listener {

        /**
         * Called when the inbox is updated.
         */
        void onInboxUpdated();
    }

    /**
     * A callback used to be notified when refreshing messages.
     */
    public interface FetchMessagesCallback {

        /**
         * Called when a request to refresh messages is finished.
         *
         * @param success If the request was successful or not.
         */
        void onFinished(boolean success);
    }


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
    private RichPushUser user;
    private final Executor executor;

    private int fetchCount = 0;
    private BroadcastReceiver foregroundReceiver;
    private Context context;
    private Handler handler = new Handler(Looper.getMainLooper());


    public RichPushInbox(Context context, PreferenceDataStore dataStore) {
        this(context, new RichPushUser(dataStore), new RichPushResolver(context), Executors.newSingleThreadExecutor());
    }

    RichPushInbox(Context context, RichPushUser user, RichPushResolver resolver, Executor executor) {
        this.context = context.getApplicationContext();
        this.user = user;
        this.richPushResolver = resolver;
        this.executor = executor;
    }

    @Override
    protected void init() {
        if (UAStringUtil.isEmpty(user.getId())) {
            final RichPushUser.Listener userListener = new RichPushUser.Listener() {
                @Override
                public void onUserUpdated(boolean success) {
                    if (success) {
                        user.removeListener(this);
                        fetchMessages();
                    }
                }
            };

            user.addListener(userListener);
        }

        user.update(false);
        refresh();

        foregroundReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Analytics.ACTION_APP_FOREGROUND.equals(intent.getAction())) {
                    fetchMessages();
                } else {
                    Intent serviceIntent = new Intent(context, RichPushUpdateService.class)
                            .setAction(RichPushUpdateService.ACTION_SYNC_MESSAGE_STATE);
                    context.startService(serviceIntent);
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Analytics.ACTION_APP_FOREGROUND);
        filter.addAction(Analytics.ACTION_APP_BACKGROUND);

        LocalBroadcastManager.getInstance(context).registerReceiver(foregroundReceiver, filter);
    }

    @Override
    protected void tearDown() {
        if (foregroundReceiver != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(foregroundReceiver);
            foregroundReceiver = null;
        }
    }

    /**
     * Returns the {@link RichPushUser}.
     * @return The {@link RichPushUser}.
     */
    public RichPushUser getUser() {
        return user;
    }

    /**
     * Starts an activity that can display the Message Center. An implicit intent with the intent
     * action {@code com.urbanairship.VIEW_RICH_PUSH_INBOX} will be attempted first. If the intent
     * fails to start an activity, the {@link InboxActivity} will be started instead.
     */
    public void startInboxActivity() {
        Intent intent = new Intent(RichPushInbox.VIEW_INBOX_INTENT_ACTION)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (intent.resolveActivity(context.getPackageManager()) == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Fallback to our InboxActivity
            intent.setClass(context, InboxActivity.class);
        }

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Logger.error("Failed to display inbox. No activities available.");
            return;
        }

        context.startActivity(intent);
    }

    /**
     * Starts an activity that can display a {@link RichPushMessage}. An implicit intent with the intent
     * action {@code com.urbanairship.VIEW_RICH_PUSH_MESSAGE} with the message ID supplied as the data
     * in the form of {@code message:<MESSAGE_ID>} will be attempted first. If the intent
     * fails to start an activity, the {@link MessageActivity} will be started instead.
     *
     * @param messageId An ID of a {@link RichPushMessage} to display.
     */
    public void startMessageActivity(@NonNull String messageId) {
        Intent intent = new Intent()
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, messageId, null));

        // Try VIEW_MESSAGE_INTENT_ACTION first
        intent.setAction(RichPushInbox.VIEW_MESSAGE_INTENT_ACTION);


        if (intent.resolveActivity(context.getPackageManager()) == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // Fallback to our MessageActivity
            intent.setClass(context, MessageActivity.class);
        }

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            Logger.error("Failed to display message. No activities available.");
            return;
        }

        context.startActivity(intent);
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
     * Fetches the latest inbox changes from Urban Airship.
     * <p/>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p/>
     * If the fetch request completes and results in a change to the messages,
     * {@link Listener#onInboxUpdated()} will be called.
     */
    public void fetchMessages() {
        fetchMessages(false, null);
    }

    /**
     * Fetches the latest inbox changes from Urban Airship.
     * <p/>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p/>
     * If the fetch request completes and results in a change to the messages,
     * {@link Listener#onInboxUpdated()} will be called.
     *
     * @param callback Callback to be notified when the request finishes fetching the messages.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public Cancelable fetchMessages(@Nullable final FetchMessagesCallback callback) {
        return fetchMessages(callback != null, callback);
    }

    /**
     * Fetches the latest inbox changes from Urban Airship.
     * <p/>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p/>
     * If the fetch request completes and results in a change to the messages,
     * {@link Listener#onInboxUpdated()} will be called.
     *
     * @param force {@code true} to force a sync request even if a request is already in progress.
     * @param callback Callback to be notified when the request finishes refreshing
     * the messages. If force is {@code false}, the callback will not be called if a sync request
     * is already in progress.
     * @return A cancelable object that can be used to cancel the callback.
     */
    private Cancelable fetchMessages(final boolean force, @Nullable final FetchMessagesCallback callback) {

        final PendingResult<Boolean> pendingResult = new PendingResult<>(new PendingResult.ResultCallback<Boolean>() {
            @Override
            public void onResult(@Nullable Boolean result) {
                if (callback != null) {
                    callback.onFinished(result != null && result);
                }
            }
        });

        if (fetchCount > 0 && !force) {
            Logger.debug("Skipping refresh messages, messages are already refreshing. Callback will not be triggered.");
            pendingResult.cancel();
            return pendingResult;
        }

        fetchCount++;

        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.myLooper())) {
            @Override
            public void onReceiveResult(int resultCode, Bundle resultData) {
                fetchCount--;
                pendingResult.setResult(resultCode == RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS);
            }
        };

        Logger.debug("RichPushInbox - Starting update service.");
        Context context = UAirship.getApplicationContext();
        Intent intent = new Intent(context, RichPushUpdateService.class)
                .setAction(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        context.startService(intent);

        return pendingResult;
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

            notifyInboxUpdated();
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

        notifyInboxUpdated();
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

        notifyInboxUpdated();
    }

    /**
     * Refreshes the inbox messages from the DB.
     */
    void refresh() {

        List<RichPushMessage> messageList = richPushResolver.getMessages();

        // Sync the messages
        synchronized (inboxLock) {

            // Save the unreadMessageIds
            Set<String> previousUnreadMessageIds = new HashSet<>(unreadMessages.keySet());
            Set<String> previousReadMessageIds = new HashSet<>(readMessages.keySet());

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
                if (previousUnreadMessageIds.contains(message.getMessageId())) {
                    message.unreadClient = true;
                    unreadMessages.put(message.getMessageId(), message);
                    continue;
                }

                // Read - check the previousUnreadMessageIds if any mark reads are still in process
                if (previousReadMessageIds.contains(message.getMessageId())) {
                    message.unreadClient = false;
                    readMessages.put(message.getMessageId(), message);
                    continue;
                }

                // Otherwise fallback to the current state
                if (message.unreadClient) {
                    unreadMessages.put(message.getMessageId(), message);
                } else {
                    readMessages.put(message.getMessageId(), message);
                }
            }
        }

        notifyInboxUpdated();
    }

    /**
     * Notifies all of the registered listeners that the
     * inbox updated.
     */
    private void notifyInboxUpdated() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (listeners) {
                    for (Listener listener : new ArrayList<>(listeners)) {
                        listener.onInboxUpdated();
                    }
                }
            }
        });
    }

    static class SentAtRichPushMessageComparator implements Comparator<RichPushMessage> {
        @Override
        public int compare(RichPushMessage lhs, RichPushMessage rhs) {
            if (rhs.getSentDateMS() == lhs.getSentDateMS()) {
                return lhs.getMessageId().compareTo(rhs.getMessageId());
            } else {
                return Long.valueOf(rhs.getSentDateMS()).compareTo(lhs.getSentDateMS());
            }
        }
    }
}
