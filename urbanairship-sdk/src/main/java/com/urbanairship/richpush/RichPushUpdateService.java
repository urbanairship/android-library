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

import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;

import com.urbanairship.BaseIntentService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;

/**
 * Service for updating the {@link RichPushUser} and their messages.
 *
 * @hide
 */
public class RichPushUpdateService extends BaseIntentService {

    /**
     * Starts the service in order to update just the {@link RichPushMessage}'s messages.
     */
    public static final String ACTION_RICH_PUSH_MESSAGES_UPDATE = "com.urbanairship.richpush.MESSAGES_UPDATE";

    /**
     * Starts the service in order to update just the {@link RichPushUser} itself.
     */
    public static final String ACTION_RICH_PUSH_USER_UPDATE = "com.urbanairship.richpush.USER_UPDATE";

    /**
     * Extra key for a result receiver passed in with the intent.
     */
    public static final String EXTRA_RICH_PUSH_RESULT_RECEIVER = "com.urbanairship.richpush.RESULT_RECEIVER";

    /**
     * Extra key to indicate if the rich push user needs to be updated forcefully.
     */
    public static final String EXTRA_FORCEFULLY = "com.urbanairship.richpush.EXTRA_FORCEFULLY";

    /**
     * Status code indicating an update complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_SUCCESS = 0;

    /**
     * Status code indicating an update did not complete successfully.
     */
    public static final int STATUS_RICH_PUSH_UPDATE_ERROR = 1;

    static final String LAST_MESSAGE_REFRESH_TIME = "com.urbanairship.user.LAST_MESSAGE_REFRESH_TIME";

    /**
     * RichPushUpdateService constructor.
     */
    public RichPushUpdateService() {
        super("RichPushUpdateService");
    }

    @Override
    protected Delegate getServiceDelegate(@NonNull String intentAction, @NonNull PreferenceDataStore dataStore) {
        Logger.verbose("RichPushUpdateService - Service delegate for intent: " + intentAction);

        switch(intentAction) {
            case ACTION_RICH_PUSH_USER_UPDATE:
                return new UserServiceDelegate(getApplicationContext(), dataStore);

            case ACTION_RICH_PUSH_MESSAGES_UPDATE:
                return new InboxServiceDelegate(getApplicationContext(), dataStore);
        }
        return  null;
    }

    /**
     * Helper method to respond to result receiver.
     *
     * @param intent The received intent.
     * @param status If the intent was successful or not.
     */
    static void respond(Intent intent, boolean status) {
        ResultReceiver receiver = intent.getParcelableExtra(EXTRA_RICH_PUSH_RESULT_RECEIVER);
        if (receiver != null) {
            if (status) {
                receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_SUCCESS, new Bundle());
            } else {
                receiver.send(RichPushUpdateService.STATUS_RICH_PUSH_UPDATE_ERROR, new Bundle());
            }
        }
    }
}
