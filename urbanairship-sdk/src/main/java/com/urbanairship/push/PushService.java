/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.support.annotation.NonNull;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;


/**
 * Service class for handling push notifications.
 *
 * @hide
 */
public class PushService extends BaseIntentService {

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
     * Action sent when a push is received by GCM.
     */
    static final String ACTION_RECEIVE_GCM_MESSAGE = "com.urbanairship.push.ACTION_RECEIVE_GCM_MESSAGE";

    /**
     * Action sent when a push is received by ADM.
     */
    static final String ACTION_RECEIVE_ADM_MESSAGE = "com.urbanairship.push.ACTION_RECEIVE_ADM_MESSAGE";

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
     * Extra containing the received message intent for {@link #ACTION_RECEIVE_ADM_MESSAGE} and {@link #ACTION_RECEIVE_GCM_MESSAGE} intent actions.
     */
    static final String EXTRA_INTENT = "com.urbanairship.push.EXTRA_INTENT";

    /**
     * Extra flag for {@link #ACTION_UPDATE_PUSH_REGISTRATION} to clear the GCM Instance ID token
     * when updating push registration.
     */
    static final String EXTRA_GCM_TOKEN_REFRESH = "com.urbanairship.push.EXTRA_GCM_TOKEN_REFRESH";

    /**
     * PushService constructor.
     */
    public PushService() {
        super("PushService");
    }

    @Override
    protected Delegate getServiceDelegate(@NonNull String intentAction, @NonNull PreferenceDataStore dataStore) {
        Logger.verbose("PushService - Service delegate for intent: " + intentAction);

        switch (intentAction) {
            case ACTION_UPDATE_NAMED_USER_TAGS:
            case ACTION_UPDATE_CHANNEL_TAG_GROUPS:
            case ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
                return new TagGroupServiceDelegate(getApplicationContext(), dataStore);

            case ACTION_UPDATE_NAMED_USER:
                return new NamedUserServiceDelegate(getApplicationContext(), dataStore);

            case ACTION_ADM_REGISTRATION_FINISHED:
            case ACTION_START_REGISTRATION:
            case ACTION_UPDATE_CHANNEL_REGISTRATION:
            case ACTION_UPDATE_PUSH_REGISTRATION:
                return new ChannelServiceDelegate(getApplicationContext(), dataStore);

            case ACTION_RECEIVE_ADM_MESSAGE:
            case ACTION_RECEIVE_GCM_MESSAGE:
                return new IncomingPushServiceDelegate(getApplicationContext(), dataStore);
        }

        return null;
    }
}
