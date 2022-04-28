/* Copyright Airship and Contributors */

package com.urbanairship.messagecenter;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.AirshipExecutors;
import com.urbanairship.Cancelable;
import com.urbanairship.CancelableOperation;
import com.urbanairship.Logger;
import com.urbanairship.Predicate;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.app.ActivityMonitor;
import com.urbanairship.app.ApplicationListener;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.channel.AirshipChannel;
import com.urbanairship.channel.AirshipChannel.ChannelRegistrationPayloadExtender;
import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.channel.ChannelRegistrationPayload;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.job.JobResult;
import com.urbanairship.json.JsonMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The inbox provides access to the device's local inbox data.
 * Modifications (e.g., deletions or mark read) will be sent to the Airship
 * server the next time the inbox is synchronized.
 */
public class Inbox {

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

    private static final SentAtRichPushMessageComparator MESSAGE_COMPARATOR = new SentAtRichPushMessageComparator();

    private final static Object inboxLock = new Object();
    private final List<InboxListener> listeners = new CopyOnWriteArrayList<>();

    private final Set<String> deletedMessageIds = new HashSet<>();
    private final Map<String, Message> unreadMessages = new HashMap<>();
    private final Map<String, Message> readMessages = new HashMap<>();
    private final Map<String, Message> messageUrlMap = new HashMap<>();

    private final MessageDao messageDao;
    private final User user;
    private final Executor executor;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final PreferenceDataStore dataStore;
    private final JobDispatcher jobDispatcher;
    private final ApplicationListener applicationListener;
    private final AirshipChannelListener channelListener;
    private final ChannelRegistrationPayloadExtender channelRegistrationPayloadExtender;
    private final User.Listener userListener;
    private final ActivityMonitor activityMonitor;
    private final AirshipChannel airshipChannel;

    private boolean isFetchingMessages = false;
    @Nullable
    @VisibleForTesting
    InboxJobHandler inboxJobHandler;

    private final AtomicBoolean isEnabled = new AtomicBoolean(false);
    private final AtomicBoolean isStarted = new AtomicBoolean(false);

    private final List<PendingFetchMessagesCallback> pendingFetchCallbacks = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param dataStore The preference data store.
     * @hide
     */
    public Inbox(@NonNull Context context, @NonNull PreferenceDataStore dataStore,
                 @NonNull AirshipChannel airshipChannel, @NonNull AirshipConfigOptions configOptions) {
        this(context, dataStore, JobDispatcher.shared(context), new User(dataStore, airshipChannel),
                MessageDatabase.createDatabase(context, configOptions).getDao(),
                AirshipExecutors.newSerialExecutor(),
                GlobalActivityMonitor.shared(context), airshipChannel);
    }

    /**
     * @hide
     */
    @VisibleForTesting
    Inbox(@NonNull Context context, @NonNull PreferenceDataStore dataStore, @NonNull final JobDispatcher jobDispatcher,
          @NonNull User user, @NonNull MessageDao messageDao, @NonNull Executor executor,
          @NonNull ActivityMonitor activityMonitor, @NonNull AirshipChannel airshipChannel) {
        this.context = context.getApplicationContext();
        this.dataStore = dataStore;
        this.user = user;
        this.messageDao = messageDao;
        this.executor = executor;
        this.jobDispatcher = jobDispatcher;
        this.airshipChannel = airshipChannel;
        this.applicationListener = new ApplicationListener() {
            @Override
            public void onForeground(long time) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                         .setAirshipComponent(MessageCenter.class)
                                         .setConflictStrategy(JobInfo.KEEP)
                                         .build();

                jobDispatcher.dispatch(jobInfo);
            }

            @Override
            public void onBackground(long time) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_SYNC_MESSAGE_STATE)
                                         .setAirshipComponent(MessageCenter.class)
                                         .setConflictStrategy(JobInfo.KEEP)
                                         .build();

                jobDispatcher.dispatch(jobInfo);
            }
        };
        this.channelListener = new AirshipChannelListener() {
            @Override
            public void onChannelCreated(@NonNull String channelId) {
                dispatchUpdateUserJob(true);
            }

            @Override
            public void onChannelUpdated(@NonNull String channelId) {
            }
        };
        this.channelRegistrationPayloadExtender = new ChannelRegistrationPayloadExtender() {
            @NonNull
            @Override
            public ChannelRegistrationPayload.Builder extend(@NonNull ChannelRegistrationPayload.Builder builder) {
                return builder.setUserId(getUser().getId());
            }
        };
        this.userListener = new User.Listener() {
            @Override
            public void onUserUpdated(boolean success) {
                if (success) {
                    fetchMessages();
                }
            }
        };
        this.activityMonitor = activityMonitor;
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void init() {
        updateEnabledState();
    }

    /**
     * @hide
     */
    @WorkerThread
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    JobResult onPerformJob(@NonNull UAirship airship, @NonNull JobInfo jobInfo) {
        if (!isEnabled.get()) {
            return JobResult.SUCCESS;
        }

        if (inboxJobHandler == null) {
            inboxJobHandler = new InboxJobHandler(context, this, getUser(), airshipChannel,
                    airship.getRuntimeConfig(), dataStore, messageDao);
        }

        return inboxJobHandler.performJob(jobInfo);
    }

    /**
     * Initializes or tears down the Inbox based on the current enabled state.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void updateEnabledState() {
        if (isEnabled.get()) {
            if (!isStarted.getAndSet(true)) {
                // Refresh the inbox whenever the user is updated.
                user.addListener(userListener);

                refresh(false);

                activityMonitor.addApplicationListener(applicationListener);
                airshipChannel.addChannelListener(channelListener);

                if (user.shouldUpdate()) {
                    dispatchUpdateUserJob(true);
                }

                airshipChannel.addChannelRegistrationPayloadExtender(channelRegistrationPayloadExtender);
            }
        } else {
            // Clean up any Message Center data stored on the device.
            deleteAllMessages();
            InboxJobHandler jobHandler = inboxJobHandler;
            if (jobHandler != null) {
                jobHandler.removeStoredData();
            }

            tearDown();
        }
    }

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void tearDown() {
        activityMonitor.removeApplicationListener(applicationListener);
        airshipChannel.removeChannelListener(channelListener);
        airshipChannel.removeChannelRegistrationPayloadExtender(channelRegistrationPayloadExtender);
        user.removeListener(userListener);
        isStarted.set(false);
    }

    /**
     * Enables or disables the Inbox.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void setEnabled(boolean isEnabled) {
        this.isEnabled.set(isEnabled);
    }

    /**
     * Returns the {@link User}.
     *
     * @return The {@link User}.
     */
    @NonNull
    public User getUser() {
        return user;
    }

    /**
     * Subscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link InboxListener} interface.
     */
    public void addListener(@NonNull InboxListener listener) {
        listeners.add(listener);
    }

    /**
     * Unsubscribe a listener for inbox update event callbacks.
     *
     * @param listener An object implementing the {@link InboxListener} interface.
     */
    public void removeListener(@NonNull InboxListener listener) {
        listeners.remove(listener);
    }

    /**
     * Fetches the latest inbox changes from Airship.
     * <p>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p>
     * If the fetch request completes and results in a change to the messages,
     * {@link InboxListener#onInboxUpdated()} will be called.
     */
    public void fetchMessages() {
        fetchMessages(null, null);
    }

    /**
     * Fetches the latest inbox changes from Airship.
     * <p>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p>
     * If the fetch request completes and results in a change to the messages,
     * {@link InboxListener#onInboxUpdated()} will be called.
     *
     * @param callback Callback to be notified when the request finishes fetching the messages.
     * @return A cancelable object that can be used to cancel the callback.
     */
    @Nullable
    public Cancelable fetchMessages(@Nullable FetchMessagesCallback callback) {
        return fetchMessages(null, callback);
    }

    /**
     * Fetches the latest inbox changes from Airship.
     * <p>
     * Normally this method is not called directly as the message list is automatically fetched when
     * the application foregrounds or when a notification with an associated message is received.
     * <p>
     * If the fetch request completes and results in a change to the messages,
     * {@link InboxListener#onInboxUpdated()} will be called.
     *
     * @param callback Callback to be notified when the request finishes fetching the messages.
     * @param looper The looper to post the callback on.
     * @return A cancelable object that can be used to cancel the callback.
     */
    @Nullable
    public Cancelable fetchMessages(@Nullable Looper looper, @Nullable FetchMessagesCallback callback) {
        PendingFetchMessagesCallback cancelableOperation = new PendingFetchMessagesCallback(callback, looper);

        synchronized (pendingFetchCallbacks) {
            pendingFetchCallbacks.add(cancelableOperation);

            if (!isFetchingMessages) {
                JobInfo jobInfo = JobInfo.newBuilder()
                                         .setAction(InboxJobHandler.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                                         .setAirshipComponent(MessageCenter.class)
                                         .setConflictStrategy(JobInfo.REPLACE)
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
    @NonNull
    private Collection<Message> filterMessages(@NonNull Collection<Message> messages, @Nullable Predicate<Message> predicate) {
        List<Message> filteredMessages = new ArrayList<>();

        if (predicate == null) {
            return messages;
        }

        for (Message message : messages) {
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
     * @return List of filtered and sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getMessages(@Nullable Predicate<Message> predicate) {
        synchronized (inboxLock) {
            List<Message> messages = new ArrayList<>();
            messages.addAll(filterMessages(unreadMessages.values(), predicate));
            messages.addAll(filterMessages(readMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getMessages() {
        return getMessages(null);
    }

    /**
     * Gets a list of unread RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getUnreadMessages(@Nullable Predicate<Message> predicate) {
        synchronized (inboxLock) {
            List<Message> messages = new ArrayList<>(filterMessages(unreadMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of unread RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getUnreadMessages() {
        return getUnreadMessages(null);
    }

    /**
     * Gets a list of read RichPushMessages, filtered by the provided predicate.
     * Sorted by descending sent-at date.
     *
     * @param predicate A predicate for filtering messages. If null, no predicate will be applied.
     * @return List of sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getReadMessages(@Nullable Predicate<Message> predicate) {
        synchronized (inboxLock) {
            List<Message> messages = new ArrayList<>(filterMessages(readMessages.values(), predicate));
            Collections.sort(messages, MESSAGE_COMPARATOR);
            return messages;
        }
    }

    /**
     * Gets a list of read RichPushMessages. Sorted by descending sent-at date.
     *
     * @return List of sorted {@link Message}s.
     */
    @NonNull
    public List<Message> getReadMessages() {
        return getReadMessages(null);
    }

    /**
     * Get the {@link Message} with the corresponding message ID.
     *
     * @param messageId The message ID of the desired {@link Message}.
     * @return A {@link Message} or <code>null</code> if one does not exist.
     */
    @Nullable
    public Message getMessage(@Nullable String messageId) {
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

    /**
     * Get the {@link Message} with the corresponding message body URL.
     *
     * @param messageUrl The message body URL of the desired {@link Message}.
     * @return A {@link Message} or <code>null</code> if one does not exist.
     */
    @Nullable
    public Message getMessageByUrl(@Nullable String messageUrl) {
        if (messageUrl == null) {
            return null;
        }

        synchronized (inboxLock) {
            return messageUrlMap.get(messageUrl);
        }
    }

    // actions

    /**
     * Mark {@link Message}s read in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesRead(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<String> messageIdsList = new ArrayList<>(messageIds);
                messageDao.markMessagesRead(messageIdsList);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                Message message = unreadMessages.get(messageId);

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
     * Mark {@link Message}s unread in bulk.
     *
     * @param messageIds A set of message ids.
     */
    public void markMessagesUnread(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> messageIdsList = new ArrayList<>(messageIds);
                messageDao.markMessagesUnread(messageIdsList);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                Message message = readMessages.get(messageId);

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
     * Mark {@link Message}s deleted.
     * <p>
     * Note that in most cases these messages aren't immediately deleted on the server, but they will
     * be inaccessible on the device as soon as they're marked deleted.
     *
     * @param messageIds A set of message ids.
     */
    public void deleteMessages(@NonNull final Set<String> messageIds) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> messageIdsList = new ArrayList(messageIds);
                messageDao.markMessagesDeleted(messageIdsList);
            }
        });

        synchronized (inboxLock) {
            for (String messageId : messageIds) {

                Message message = getMessage(messageId);
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
     * Delete all message data stored on the device.
     *
     * @hide
     */
    private void deleteAllMessages() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                messageDao.deleteAllMessages();
            }
        });

        synchronized (inboxLock) {
            unreadMessages.clear();
            readMessages.clear();
            deletedMessageIds.clear();
        }

        notifyInboxUpdated();
    }

    /**
     * Refreshes the inbox messages from the DB.
     *
     * @param notify {@code true} to notify listeners, otherwise {@code false}.
     */
    void refresh(boolean notify) {

        List<MessageEntity> messageList = messageDao.getMessages();

        // Sync the messages
        synchronized (inboxLock) {

            // Save the unreadMessageIds
            Set<String> previousUnreadMessageIds = new HashSet<>(unreadMessages.keySet());
            Set<String> previousReadMessageIds = new HashSet<>(readMessages.keySet());
            Set<String> previousDeletedMessageIds = new HashSet<>(deletedMessageIds);

            // Clear the current messages
            unreadMessages.clear();
            readMessages.clear();
            messageUrlMap.clear();

            // Process the new messages
            for (MessageEntity messageEntity : messageList) {

                Message message = messageEntity.createMessageFromEntity(messageEntity);

                if (message == null) {
                    continue;
                }

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

                // Populate message url map
                messageUrlMap.put(message.getMessageBodyUrl(), message);

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
                for (InboxListener listener : listeners) {
                    listener.onInboxUpdated();
                }
            }
        });
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    void dispatchUpdateUserJob(boolean forcefully) {
        Logger.debug("Updating user.");

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(InboxJobHandler.ACTION_RICH_PUSH_USER_UPDATE)
                                 .setAirshipComponent(MessageCenter.class)
                                 .setExtras(JsonMap.newBuilder()
                                                   .put(InboxJobHandler.EXTRA_FORCEFULLY, forcefully)
                                                   .build())
                                 .setConflictStrategy(forcefully ? JobInfo.REPLACE : JobInfo.KEEP)
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    static class SentAtRichPushMessageComparator implements Comparator<Message> {

        @Override
        public int compare(@NonNull Message lhs, @NonNull Message rhs) {
            if (rhs.getSentDateMS() == lhs.getSentDateMS()) {
                return lhs.getMessageId().compareTo(rhs.getMessageId());
            } else {
                return Long.valueOf(rhs.getSentDateMS()).compareTo(lhs.getSentDateMS());
            }
        }
    }

    static class PendingFetchMessagesCallback extends CancelableOperation {

        private final FetchMessagesCallback callback;
        boolean result;

        PendingFetchMessagesCallback(FetchMessagesCallback callback, Looper looper) {
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
