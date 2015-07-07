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

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.SparseArray;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;


/**
 * Service class for handling push notifications.
 *
 * @hide
 */
public class PushService extends BaseIntentService {

    /**
     * The timeout before a wake lock is released.
     */
    private static final long WAKE_LOCK_TIMEOUT_MS = 60 * 1000; // 1 minute

    /**
     * Action to start channel and push registration.
     */
    static final String ACTION_START_REGISTRATION = "com.urbanairship.push.ACTION_START_REGISTRATION";

    /**
     * Action notifying the service that ADM registration has finished.
     */
    static final String ACTION_ADM_REGISTRATION_FINISHED = "com.urbanairship.push.ACTION_ADM_REGISTRATION_FINISHED";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_PUSH_REGISTRATION = "com.urbanairship.push.ACTION_UPDATE_PUSH_REGISTRATION";

    /**
     * Action to update channel registration.
     */
    static final String ACTION_UPDATE_CHANNEL_REGISTRATION = "com.urbanairship.push.ACTION_UPDATE_CHANNEL_REGISTRATION";

    /**
     * Action sent when a push is received.
     */
    static final String ACTION_PUSH_RECEIVED = "com.urbanairship.push.ACTION_PUSH_RECEIVED";

    /**
     * Action to update named user association or disassociation.
     */
    static final String ACTION_UPDATE_NAMED_USER = "com.urbanairship.push.ACTION_UPDATE_NAMED_USER";

    /**
     * Action to update named user tags.
     */
    static final String ACTION_UPDATE_NAMED_USER_TAGS = "com.urbanairship.push.ACTION_UPDATE_NAMED_USER_TAGS";

    /**
     * Action to update the channel tag groups.
     */
    static final String ACTION_UPDATE_CHANNEL_TAG_GROUPS = "com.urbanairship.push.ACTION_UPDATE_CHANNEL_TAG_GROUPS";

    /**
     * Action to clear the pending named user tags.
     */
    static final String ACTION_CLEAR_PENDING_NAMED_USER_TAGS = "com.urbanairship.push.ACTION_CLEAR_PENDING_NAMED_USER_TAGS";

    /**
     * Extra containing tag groups to add to channel tag groups or named user tags.
     */
    static final String EXTRA_ADD_TAG_GROUPS = "com.urbanairship.push.EXTRA_ADD_TAG_GROUPS";

    /**
     * Extra containing tag groups to remove from channel tag groups or named user tags.
     */
    static final String EXTRA_REMOVE_TAG_GROUPS = "com.urbanairship.push.EXTRA_REMOVE_TAG_GROUPS";

    /**
     * Extra for wake lock ID. Set and removed by the service.
     */
    static final String EXTRA_WAKE_LOCK_ID = "com.urbanairship.push.EXTRA_WAKE_LOCK_ID";

    private static final SparseArray<WakeLock> wakeLocks = new SparseArray<>();
    private static int nextWakeLockID = 0;

    private TagGroupServiceDelegate tagGroupServiceDelegate;
    private NamedUserServiceDelegate namedUserServiceDelegate;
    private ChannelServiceDelegate channelServiceDelegate;

    /**
     * PushService constructor.
     */
    public PushService() {
        super("PushService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        super.onHandleIntent(intent);

        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("PushService - Received intent: " + intent.getAction());


        String action = intent.getAction();
        int wakeLockId = intent.getIntExtra(EXTRA_WAKE_LOCK_ID, -1);
        intent.removeExtra(EXTRA_WAKE_LOCK_ID);

        try {
            switch (action) {
                case ACTION_PUSH_RECEIVED:
                    onPushReceived(intent);
                    break;
            }
        } finally {
            if (wakeLockId >= 0) {
                releaseWakeLock(wakeLockId);
            }
        }
    }


    @Override
    protected Delegate getServiceDelegate(String intentAction, PreferenceDataStore dataStore) {
        Logger.verbose("PushService - Service delegate for intent: " + intentAction);

        switch (intentAction) {
            case ACTION_UPDATE_NAMED_USER_TAGS:
            case ACTION_UPDATE_CHANNEL_TAG_GROUPS:
            case ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
                if (tagGroupServiceDelegate == null) {
                    tagGroupServiceDelegate = new TagGroupServiceDelegate(getApplicationContext(), dataStore);
                }
                return tagGroupServiceDelegate;
            case ACTION_UPDATE_NAMED_USER:
                if (namedUserServiceDelegate == null) {
                    namedUserServiceDelegate = new NamedUserServiceDelegate(getApplicationContext(), dataStore);
                }
                return namedUserServiceDelegate;

            case ACTION_ADM_REGISTRATION_FINISHED:
            case ACTION_START_REGISTRATION:
            case ACTION_UPDATE_CHANNEL_REGISTRATION:
            case ACTION_UPDATE_PUSH_REGISTRATION:
                if (channelServiceDelegate == null) {
                    channelServiceDelegate = new ChannelServiceDelegate(getApplicationContext(), dataStore);
                }
                return channelServiceDelegate;
        }

        return null;
    }

    /**
     * The PushMessage will be parsed from the intent and delivered to
     * the PushManager.
     *
     * @param intent The value passed to onHandleIntent.
     */
    private void onPushReceived(Intent intent) {
        PushMessage message = new PushMessage(intent.getExtras());
        Logger.info("Received push message: " + message);
        UAirship.shared().getPushManager().deliverPush(message);
    }

    /**
     * Start the <code>Push Service</code>.
     *
     * @param context The context in which the receiver is running.
     * @param intent The intent to start the service.
     */
    static void startServiceWithWakeLock(final Context context, Intent intent) {
        intent.setClass(context, PushService.class);

        // Acquire a wake lock and add the id to the intent
        intent.putExtra(EXTRA_WAKE_LOCK_ID, acquireWakeLock());

        context.startService(intent);
    }

    /**
     * Releases a wake lock.
     *
     * @param wakeLockId The id of the wake lock to release.
     */
    private static synchronized void releaseWakeLock(int wakeLockId) {
        Logger.verbose("PushService - Releasing wake lock: " + wakeLockId);

        WakeLock wakeLock = wakeLocks.get(wakeLockId);

        if (wakeLock != null) {
            wakeLocks.remove(wakeLockId);

            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    /**
     * Acquires a new wake lock.
     *
     * @return id of the wake lock.
     */
    private static synchronized int acquireWakeLock() {
        Context context = UAirship.getApplicationContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UA_GCM_WAKE_LOCK");
        wakeLock.setReferenceCounted(false);
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS);

        wakeLocks.append(++nextWakeLockID, wakeLock);

        Logger.verbose("PushService - Acquired wake lock: " + nextWakeLockID);

        return nextWakeLockID;
    }

}
