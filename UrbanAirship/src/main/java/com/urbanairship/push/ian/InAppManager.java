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

package com.urbanairship.push.ian;

import com.urbanairship.BaseManager;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.json.JsonException;

/**
 * This class is the primary interface for interacting with in app notifications.
 */
public class InAppManager extends BaseManager {

    private String PENDING_IN_APP_NOTIFICATION = "com.urbanairship.push.ian.PENDING_IN_APP_NOTIFICATION";

    private final PreferenceDataStore dataStore;

    /**
     * Default constructor.
     *
     * @param dataStore The preference data store.
     * @hide
     */
    public InAppManager(PreferenceDataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Sets the pending notification.
     *
     * @param notification The InAppNotification.
     */
    public void setPendingNotification(InAppNotification notification) {
        if (notification == null) {
            dataStore.remove(PENDING_IN_APP_NOTIFICATION);
        } else {
            dataStore.put(PENDING_IN_APP_NOTIFICATION, notification.toJsonValue().toString());
        }
    }

    /**
     * Gets the pending notification.
     *
     * @return  The pending InAppNotification.
     */
    public InAppNotification getPendingNotification() {
        String payload = dataStore.getString(PENDING_IN_APP_NOTIFICATION, null);
        if (payload == null) {
            return null;
        }

        try {
            return InAppNotification.parseJson(payload);
        } catch (JsonException e) {
            Logger.error("InAppManager - Failed to read pending in app notification: " + payload, e);
            setPendingNotification(null);
            return null;
        }
    }
}
