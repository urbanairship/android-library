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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;

import com.urbanairship.BaseManager;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class is the primary interface for Rich Push functionality.
 */
public class RichPushManager extends BaseManager {

    /**
     * The rich push extra that contains the rich push message ID.
     */
    public static final String RICH_PUSH_KEY = "_uamid";

    private final RichPushUser user;
    private final RichPushInbox inbox;

    // Number of refresh message requests currently in flight.
    private final AtomicInteger refreshMessageRequestCount = new AtomicInteger();

    private final List<Listener> listeners = new ArrayList<>();

    private BroadcastReceiver foregroundReceiver;

    /**
     * Creates a RichPushManager. Normally only one rich push manager instance should exist, and
     * can be accessed from {@link com.urbanairship.UAirship#getRichPushManager()}.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     *
     * @hide
     */
    public RichPushManager(Context context, PreferenceDataStore preferenceDataStore) {
        this(new RichPushUser(preferenceDataStore), new RichPushInbox(context));
    }

    /**
     * Creates a RichPushManager.
     *
     * @param user The RichPushUser.
     * @param inbox The RichPushInbox.
     */
    RichPushManager(@NonNull RichPushUser user, @NonNull RichPushInbox inbox) {
        this.user = user;
        this.inbox = inbox;
    }

    @Override
    protected void init() {
        inbox.updateCache();
        updateUser(false);

        foregroundReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                refreshMessages();
            }
        };

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(UAirship.getApplicationContext());

        IntentFilter filter = new IntentFilter();
        filter.addAction(Analytics.ACTION_APP_FOREGROUND);

        broadcastManager.registerReceiver(foregroundReceiver, filter);
        UAirship.getApplicationContext().registerReceiver(foregroundReceiver, filter);
    }

    @Override
    protected void tearDown() {
        if (foregroundReceiver != null) {
            UAirship.getApplicationContext().unregisterReceiver(foregroundReceiver);
            foregroundReceiver = null;
        }
    }

    /**
     * Indicates whether messages are currently being retrieved from the server.
     *
     * @return <code>true</code> if a refresh is in progress, <code>false</code> otherwise.
     */
    public boolean isRefreshingMessages() {
        return refreshMessageRequestCount.get() > 0;
    }

    /**
     * Subscribe a listener for inbox and user update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushManager.Listener} interface.
     */
    public void addListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Unsubscribe a listener for inbox and user update event callbacks.
     *
     * @param listener An object implementing the {@link RichPushManager.Listener} interface.
     */
    public void removeListener(@NonNull Listener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * A listener interface for receiving event callbacks related to inbox and user updates.
     */
    public interface Listener {
        void onUpdateMessages(boolean success);

        void onUpdateUser(boolean success);
    }

    /**
     * A callback used to be notified when refreshing messages.
     */
    public interface RefreshMessagesCallback {
        void onRefreshMessages(boolean success);
    }

    /**
     * Get the {@link RichPushUser}.
     *
     * @return {@link RichPushUser}.
     */
    @NonNull
    public synchronized RichPushUser getRichPushUser() {
        return this.user;
    }

    /**
     * Get the {@link com.urbanairship.richpush.RichPushInbox}.
     *
     * @return {@link com.urbanairship.richpush.RichPushInbox}.
     */
    @NonNull
    public synchronized RichPushInbox getRichPushInbox() {
        return this.inbox;
    }


    // actions
    /**
     * Sync the messages on the device with what's on the server.
     * <p/>
     * The inbox should be updated automatically.
     */
    public void refreshMessages() {
        refreshMessages(false);
    }

    /**
     * Sync the messages on the device with what's on the server.
     * <p/>
     * The inbox should be updated automatically.
     *
     * @param force <code>true</code> to refresh messages even if messages are
     * being refreshed.
     */
    public void refreshMessages(boolean force) {
        refreshMessages(force, null);
    }

    /**
     * Sync the messages on the device with what's on the server.
     * <p/>
     * The inbox should be updated automatically.
     * <p/>
     * Note: The listeners will be called after all of the refresh message
     * requests are finished, while the callback will be called after this particular
     * refresh messages request is finished.
     *
     * @param callback Callback to be notified when the request finishes refreshing
     * the messages.
     */
    public void refreshMessages(@Nullable RefreshMessagesCallback callback) {
        refreshMessages(true, callback);
    }

    /**
     * Sync the messages on the device with what's on the server.
     * <p/>
     * The inbox should be updated automatically.
     * <p/>
     * Note: The listeners will be called after all of the refresh message
     * requests are finished, while the callback will be called after this particular
     * refresh messages request is finished.
     *
     * @param force <code>true</code> to refresh messages even if messages are
     * being refreshed.
     * @param callback Callback to be notified when the request finishes refreshing
     * the messages.
     */
    private void refreshMessages(boolean force, @Nullable final RefreshMessagesCallback callback) {
        if (isRefreshingMessages() && !force) {
            Logger.debug("Skipping refresh messages, messages are already refreshing. Callback will not be triggered.");
            return;
        }

        final int requestNumber = refreshMessageRequestCount.incrementAndGet();

        UpdateResultReceiver resultReceiver = new UpdateResultReceiver() {
            @Override
            public void onUpdate(boolean success) {
                // if the request number matches the current refresh message
                // request count, then its the last request made and we
                // should reset the count and notify the listeners of the result
                if (refreshMessageRequestCount.compareAndSet(requestNumber, 0)) {
                    onMessagesUpdate(success);
                }

                if (callback != null) {
                    callback.onRefreshMessages(success);
                }
            }
        };

        Logger.debug("RichPushManager - Starting update service.");
        Context context = UAirship.getApplicationContext();
        Intent intent = new Intent(context, RichPushUpdateService.class)
                .setAction(RichPushUpdateService.ACTION_RICH_PUSH_MESSAGES_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver);

        context.startService(intent);
    }

    /**
     * Sync the user on the device with what's on the server.
     *
     * @deprecated Marked to be removed in 7.0.0. Use updateUser(true) instead.
     */
    @Deprecated
    public void updateUser() {
        updateUser(true);
    }

    /**
     * Updates the user if the user has not been updated
     * in the last 24hrs.
     *
     * @deprecated Marked to be removed in 7.0.0. Use updateUser(false) instead.
     */
    @Deprecated
    public void updateUserIfNecessary() {
        updateUser(false);
    }

    /**
     * Updates the user on the device with what's on the server.
     *
     * @param forcefully A boolean indicating if the rich push user needs to be updated.
     */
    public void updateUser(boolean forcefully) {
        UpdateResultReceiver resultReceiver = new UpdateResultReceiver() {
            @Override
            public void onUpdate(boolean success) {
                onUserUpdate(success);
            }
        };

        Logger.debug("RichPushManager - Starting update service.");
        Context context = UAirship.getApplicationContext();
        Intent intent = new Intent(context, RichPushUpdateService.class)
                .setAction(RichPushUpdateService.ACTION_RICH_PUSH_USER_UPDATE)
                .putExtra(RichPushUpdateService.EXTRA_RICH_PUSH_RESULT_RECEIVER, resultReceiver)
                .putExtra(RichPushUpdateService.EXTRA_FORCEFULLY, forcefully);

        context.startService(intent);
    }

    /**
     * Gets a copy of the listeners
     *
     * @return A new copy of the listeners
     */
    @NonNull
    private List<Listener> getListeners() {
        synchronized (listeners) {
            return new ArrayList<>(listeners);
        }
    }

    /**
     * Set the user last update time if user update succeeded
     *
     * @param success A boolean indicating whether user update succeeded
     */
    private void onUserUpdate(boolean success) {
        for (Listener l : getListeners()) {
            try {
                l.onUpdateUser(success);
            } catch (Exception e) {
                Logger.error("RichPushManager - Unable to complete onUpdateUser() callback.", e);
            }
        }
    }

    /**
     * Listen for inbox message update events
     *
     * @param success A boolean indicating whether inbox message update succeeded
     */
    private void onMessagesUpdate(boolean success) {
        for (Listener l : getListeners()) {
            try {
                l.onUpdateMessages(success);
            } catch (Exception e) {
                Logger.error("RichPushManager - Unable to complete onUpdateMessages() callback.", e);
            }
        }
    }

    /**
     * Indicates whether a push belongs to a Rich Push message.
     * <p/>
     * This is used for incoming push messages.
     *
     * @param extras Push message extras.
     * @return <code>true</code> if the extras belong to a rich push message, <code>false</code> otherwise.
     */
    public static boolean isRichPushMessage(Map<String, String> extras) {
        return extras.containsKey(RICH_PUSH_KEY);
    }

    /**
     * Indicates whether a push belongs to a Rich Push message.
     * <p/>
     * This is used for determining whether an opened notification is a rich push message.
     *
     * @param extras Notification Intent extras.
     * @return <code>true</code> if the extras belong to a rich push message, <code>false</code> otherwise.
     */
    public static boolean isRichPushMessage(Bundle extras) {
        return extras.containsKey(RICH_PUSH_KEY);
    }

    private static abstract class UpdateResultReceiver extends ResultReceiver {
        public UpdateResultReceiver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            boolean success = resultCode == RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS;
            onUpdate(success);
        }

        public abstract void onUpdate(boolean success);
    }
}
