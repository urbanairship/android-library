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

    private TagGroupServiceDelegate tagGroupServiceDelegate;
    private NamedUserServiceDelegate namedUserServiceDelegate;
    private ChannelServiceDelegate channelServiceDelegate;
    private IncomingPushServiceDelegate incomingPushServiceDelegate;

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

            case ACTION_RECEIVE_ADM_MESSAGE:
            case ACTION_RECEIVE_GCM_MESSAGE:
                if (incomingPushServiceDelegate == null) {
                    incomingPushServiceDelegate = new IncomingPushServiceDelegate(getApplicationContext(), dataStore);
                }
                return incomingPushServiceDelegate;
        }

        return null;
    }
}
