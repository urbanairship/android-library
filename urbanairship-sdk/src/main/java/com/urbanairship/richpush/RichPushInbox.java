/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.richpush;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.AirshipComponent;
import com.urbanairship.Cancelable;
import com.urbanairship.CancelableOperation;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.messagecenter.MessageActivity;
import com.urbanairship.messagecenter.MessageCenterActivity;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.Collection;
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
     * Predicate interface for {@link RichPushMessage}.
     */
    public interface Predicate {

        /**
         * Applies the predicate to the provided message.
         *
         * @param message A {@link RichPushMessage} instance.
         * @return {@code true} if the message matches the predicate, otherwise {@code false}.
         */
        boolean apply(RichPushMessage message);
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
    private final RichPushUser user;
    private final Executor executor;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PreferenceDataStore dataStore;
    private final JobDispatcher jobDispatcher;
    private final ActivityMonitor.Listener listener;
    private final ActivityMonitor activityMonitor;

    private boolean isFetchingMessages = false;
    private InboxJobHandler inboxJobHandler;


    private final List<PendingFetchMessagesCallback> pendingFetchCallbacks = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @hide
     */
    public RichPushInbox(Context context, PreferenceDataStore dataStore, ActivityMonitor activityMonitor) {
        this(context, dataStore, JobDispatcher.shared(context), new RichPushUser(dataStore, JobDispatcher.shared(context)),
                new RichPushResolver(context), Executors.newSingleThreadExecutor(), activityMonitor);
    }

    /**
     * @hide
     */
    @VisibleForTesting
    RichPushInbox(Context context, PreferenceDataStore dataStore, final JobDispatcher jobDispatcher,
                  RichPushUser user, RichPushResolver resolver, Executor executor, ActivityMonitor activityMonitor) {
        this.context = context.getApplicationContext();
        this.dataStore = dataStore;
        this.user = user;
        this.richPushResolver = resolver;
        this.executor = executor;
        this.jobDispatcher = jobDispatcher;
        this.listener = new ActivityMonitor.Listener() {
            @Override
            public void onForeground(long time) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                         .setAirshipComponent(RichPushInbox.class)
                                         .build();

                jobDispatcher.dispatch(jobInfo);
            }

            @Override
            public void onBackground(long time) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_SYNC_MESSAGE_STATE)
                                         .setId(JobInfo.RICH_PUSH_SYNC_MESSAGE_STATE)
                                         .setAirshipComponent(RichPushInbox.class)
                                         .build();

                jobDispatcher.dispatch(jobInfo);
            }
        };
        this.activityMonitor = activityMonitor;
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
        } else {
            user.update(false);
        }

        refresh(false);

        activityMonitor.addListener(listener);
    }

    /**
     * @hide
     */
    @Override
    @WorkerThread
    @JobInfo.JobResult
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public int onPerformJob(@NonNull UAirship airship, JobInfo jobInfo) {
        if (inboxJobHandler == null) {
            inboxJobHandler = new InboxJobHandler(context, airship, dataStore);
        }

        return inboxJobHandler.performJob(jobInfo);
    }

    @Override
    protected void tearDown() {
        activityMonitor.removeListener(listener);
    }

    /**
     * Returns the {@link RichPushUser}.
     *
     * @return The {@link RichPushUser}.
     */
    public RichPushUser getUser() {
        return user;
    }

    /**
     * Starts an activity that can display the Message Center. An implicit intent with the intent
     * action {@code com.urbanairship.VIEW_RICH_PUSH_INBOX} will be attempted first. If the intent
     * fails to start an activity, the {@link MessageCenterActivity} will be started instead.
     */
    public void startInboxActivity() {
        Intent intent = new Intent(RichPushInbox.VIEW_INBOX_INTENT_ACTION)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            // Fallback to our MessageCenterActivity
            intent.setClass(context, MessageCenterActivity.class);
        }

        context.startActivity(intent);
    }

    /**
     * Starts an activity that can display a {@link RichPushMessage}. An implicit intent with the intent
     * action {@code com.urbanairship.VIEW_RICH_PUSH_INBOX} with the message ID supplied as the data
     * in the form of {@code message:<MESSAGE_ID>} will be attempted first. If the intent
     * fails then the action {@code com.urbanairship.VIEW_MESSAGE_INTENT_ACTION} will be tried. Finally,
     * it will fall back to the {@link MessageActivity} activity.
     *
     * @param messageId An ID of a {@link RichPushMessage} to display.
     */
    public void startMessageActivity(@NonNull String messageId) {
        Intent intent = new Intent(RichPushInbox.VIEW_INBOX_INTENT_ACTION)
                .setPackage(context.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setData(Uri.fromParts(RichPushInbox.MESSAGE_DATA_SCHEME, messageId, null));

        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
            return;
        }

        intent.setAction(RichPushInbox.VIEW_MESSAGE_INTENT_ACTION);

        if (intent.resolveActivity(context.getPackageManager()) == null) {
            // Fallback to our MessageCenterActivity
            intent.setClass(context, MessageCenterActivity.class);
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
        fetchMessages(null, null);
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
    public Cancelable fetchMessages(@NonNull final FetchMessagesCallback callback) {
        return fetchMessages(callback, null);
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
     * @param looper The looper to post the callback on.
     * @return A cancelable object that can be used to cancel the callback.
     */
    public Cancelable fetchMessages(final FetchMessagesCallback callback, Looper looper) {
        PendingFetchMessagesCallback cancelableOperation = new PendingFetchMessagesCallback(callback, looper);

        synchronized (pendingFetchCallbacks) {
            pendingFetchCallbacks.add(cancelableOperation);

            if (!isFetchingMessages) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                         .setId(JobInfo.RICH_PUSH_UPDATE_MESSAGES)
                                         .setAirshipComponent(RichPushInbox.class)
                                         .build();

                jobDispatcher.dispatch(jobInfo);
            }

            isFetchingMessages = true;
        }

        return cancelableOperation;
    }


    void onUpdateMessagesFinished(boolean result) {
        synchronized (pendingFetchCallbacks) {
            for (PendingFetchMessagesCallback callback : pendingFetchCallbacks) {
                callback.result = result;
                callback.run();
            }

            isFetchingMessages = false;
            pendingFetchCallbacks.clear();
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
     * Filters a collection of messages according to the supplied predicate
     *
     * @param messages The messages to filter
     * @param predicate The predicate. If null, the collection will be returned as-is.
     * @return A filtered collection of messages
     */
    private Collection<RichPushMessage> filterMessages(Collection<RichPushMessage> messages, @Nullable Predicate predicate) {
        List<RichPushMessage> filteredMessages = new ArrayList<>();

        if (predicate == null) {
            return messages;
        }

        for (RichPushMessage message : messages) {
            if (predicate.apply(message)) {
                filteredMessages.add(message);
            }
        }

        return filteredMessages;
    }

    /**
     * Gets a list of RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of filtered and sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getMessages(@Nullable Predicate predicate) {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>();
            messages.addAll(filterMessages(unreadMessages.values(), predicate));
            messages.addAll(filterMessages(readMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }


    /**
     * Gets a list of RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getMessages() {
        return getMessages(null);
    }

    /**
     * Gets a list of unread RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getUnreadMessages(@Nullable Predicate predicate) {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>(filterMessages(unreadMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of unread RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getUnreadMessages() {
        return getUnreadMessages(null);
    }

    /**
     * Gets a list of read RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getReadMessages(@Nullable Predicate predicate) {
        synchronized (inboxLock) {
            List<RichPushMessage> messages = new ArrayList<>(filterMessages(readMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of read RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link RichPushMessage}s.
     */
    @NonNull
    public List<RichPushMessage> getReadMessages() {
        return getReadMessages(null);
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
     *
     * @param notify {@code true} to notify listeners, otherwise {@code false}.
     */
    void refresh(boolean notify) {

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

                // Expired
                if (message.isExpired()) {
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

        if (notify) {
            notifyInboxUpdated();
        }
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


    static class PendingFetchMessagesCallback extends CancelableOperation {

        private FetchMessagesCallback callback;
        boolean result;

        public PendingFetchMessagesCallback(FetchMessagesCallback callback, Looper looper) {
            super(looper);
            this.callback = callback;
        }

        @Override
        protected void onRun() {
            if (callback != null) {
                callback.onFinished(result);
            }
        }
    }

}
