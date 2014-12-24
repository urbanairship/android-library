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

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

/**
 * The named user is created in the PushManager.
 */
public class NamedUser {

    /**
     * The current named user ID.
     */
    private static final String CURRENT_NAMED_USER_ID_KEY = "com.urbanairship.nameduser.CURRENT_NAMED_USER_ID";

    /**
     * The associated named user ID.
     */
    private static final String ASSOCIATED_NAMED_USER_ID_KEY = "com.urbanairship.nameduser.ASSOCIATED_NAMED_USER_ID";

    /**
     * The maximum length of the named user ID string.
     */
    private static final int MAX_NAMED_USER_ID_LENGTH = 128;

    private final PreferenceDataStore preferenceDataStore;


    /**
     * Creates a NamedUser.
     *
     * @param preferenceDataStore The preferences data store.
     */
    NamedUser(PreferenceDataStore preferenceDataStore) {
        this.preferenceDataStore = preferenceDataStore;
    }

    /**
     * Returns the named user ID.
     *
     * @return The named user ID as a string or null if it does not exist.
     */
    public String getId() {
        return preferenceDataStore.getString(CURRENT_NAMED_USER_ID_KEY, null);
    }

    /**
     * Sets the named user ID.
     *
     * @param namedUserId The named user ID string. Length must be greater than 0 and less than 129 characters.
     */
    public void setId(String namedUserId) {
        if (namedUserId == null) {
            preferenceDataStore.remove(CURRENT_NAMED_USER_ID_KEY);
        } else {
            namedUserId = namedUserId.trim();
            if (UAStringUtil.isEmpty(namedUserId) || namedUserId.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("Failed to set named user ID. " +
                        "The named user ID must be greater than 0 and less than 129 characters.");
                return;
            }
            preferenceDataStore.put(CURRENT_NAMED_USER_ID_KEY, namedUserId);
        }

        Logger.debug("Start service to update named user.");
        startUpdateService();
    }

    /**
     * Sets the associated named user ID.
     *
     * @param id The named user ID string.
     */
    void setAssociatedId(String id) {
        if (id == null) {
            preferenceDataStore.remove(ASSOCIATED_NAMED_USER_ID_KEY);
        } else {
            preferenceDataStore.put(ASSOCIATED_NAMED_USER_ID_KEY, id);
        }
    }

    /**
     * Gets the associated named user ID.
     *
     * @return The associated named user ID or null if it does not exist.
     */
    public String getAssociatedId() {
        return preferenceDataStore.getString(ASSOCIATED_NAMED_USER_ID_KEY, null);
    }

    /**
     * Start service for named user update.
     */
    void startUpdateService() {
        Context ctx = UAirship.getApplicationContext();
        Intent i = new Intent(ctx, PushService.class);
        i.setAction(PushService.ACTION_UPDATE_NAMED_USER);
        ctx.startService(i);
    }
}
