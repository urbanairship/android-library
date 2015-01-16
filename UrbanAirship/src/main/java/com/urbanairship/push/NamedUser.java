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

import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.util.UUID;

/**
 * The named user is created in the PushManager.
 */
public class NamedUser {

    /**
     * The current named user ID.
     */
    private static final String CURRENT_NAMED_USER_ID_KEY = "com.urbanairship.nameduser.CURRENT_NAMED_USER_ID";

    /**
     * The current token tracks the start of setting the named user ID.
     */
    private static final String CURRENT_TOKEN_KEY = "com.urbanairship.nameduser.CURRENT_TOKEN_KEY";

    /**
     * The last updated token tracks when the named user ID was set successfully.
     */
    private static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

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
     * Forces a named user update.
     */
    public void forceUpdate() {
        updateChangeToken();
        startUpdateService();
    }

    /**
     * Sets the named user ID.
     * </p>
     * To associate the named user ID, its length must be greater than 0 and less than 129 characters.
     * To disassociate the named user ID, its value must be null.
     *
     * @param namedUserId The named user ID string.
     */
    public void setId(String namedUserId) {
        String id = null;
        if (namedUserId != null) {
            id = namedUserId.trim();
            if (UAStringUtil.isEmpty(id) || id.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("NamedUser - Failed to set named user ID. " +
                        "The named user ID must be greater than 0 and less than 129 characters.");
                return;
            }
        }

        // check if the newly trimmed ID matches with currently stored ID
        boolean isEqual = getId() == null ? id == null : getId().equals(id);

        // if the IDs don't match or ID is set to null and current token is null, then update.
        if (!isEqual || (getId() == null && getCurrentToken() == null)) {
            preferenceDataStore.put(CURRENT_NAMED_USER_ID_KEY, id);

            // Force update by changing the token.
            updateChangeToken();

            Logger.debug("NamedUser - Start service to update named user.");
            startUpdateService();
        } else {
            Logger.debug("NamedUser - Skipping update. Named user ID trimmed already matches existing named user: " + getId());
        }
    }

    /**
     * Gets the current named user ID token.
     *
     * @return The current named user ID token.
     */
    public String getCurrentToken() {
        return preferenceDataStore.getString(CURRENT_TOKEN_KEY, null);
    }

    /**
     * Change the current token to force an update.
     */
    void updateChangeToken() {
        preferenceDataStore.put(CURRENT_TOKEN_KEY, UUID.randomUUID().toString());
    }

    /**
     * Sets the last updated named user ID token.
     *
     * @param token The last updated named user ID token.
     */
    void setLastUpdatedToken(String token) {
        preferenceDataStore.put(LAST_UPDATED_TOKEN_KEY, token);
    }

    /**
     * Gets the last updated named user ID token.
     *
     * @return The last updated named user token.
     */
    public String getLastUpdatedToken() {
        return preferenceDataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
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
