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
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAStringUtil;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The named user is an alternate method of identifying the device. Once a named
 * user is associated to the device, it can be used to send push notifications
 * to the device.
 */
public class NamedUser {

    /**
     * The named user ID.
     */
    private static final String NAMED_USER_ID_KEY = "com.urbanairship.nameduser.NAMED_USER_ID_KEY";

    /**
     * The change token tracks the start of setting the named user ID.
     */
    private static final String CHANGE_TOKEN_KEY = "com.urbanairship.nameduser.CHANGE_TOKEN_KEY";

    /**
     * The last updated token tracks when the named user ID was set successfully.
     */
    private static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    /**
     * The maximum length of the named user ID string.
     */
    private static final int MAX_NAMED_USER_ID_LENGTH = 128;

    /**
     * The pending add tags.
     */
    private static final String PENDING_ADD_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_ADD_TAG_GROUPS_KEY";

    /**
     * The pending remove tags.
     */
    private static final String PENDING_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_REMOVE_TAG_GROUPS_KEY";

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
        return preferenceDataStore.getString(NAMED_USER_ID_KEY, null);
    }

    /**
     * Forces a named user update.
     */
    public void forceUpdate() {
        Logger.debug("NamedUser - force named user update.");
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
    public synchronized void setId(String namedUserId) {
        String id = null;
        if (namedUserId != null) {
            id = namedUserId.trim();
            if (UAStringUtil.isEmpty(id) || id.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("Failed to set named user ID. " +
                        "The named user ID must be greater than 0 and less than 129 characters.");
                return;
            }
        }

        // check if the newly trimmed ID matches with currently stored ID
        boolean isEqual = getId() == null ? id == null : getId().equals(id);

        // if the IDs don't match or ID is set to null and current token is null (re-install case), then update.
        if (!isEqual || (getId() == null && getChangeToken() == null)) {
            preferenceDataStore.put(NAMED_USER_ID_KEY, id);

            // Update the change token.
            updateChangeToken();

            // When named user ID change, clear pending named user tags.
            Logger.debug("NamedUser - Clear pending named user tags.");
            startClearPendingTagsService();

            Logger.debug("NamedUser - Start service to update named user.");
            startUpdateService();
        } else {
            Logger.debug("NamedUser - Skipping update. Named user ID trimmed already matches existing named user: " + getId());
        }
    }

    /**
     * Edit the named user tags.
     *
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
    }

    /**
     * Gets the named user ID change token.
     *
     * @return The named user ID change token.
     */
    String getChangeToken() {
        return preferenceDataStore.getString(CHANGE_TOKEN_KEY, null);
    }

    /**
     * Modify the change token to force an update.
     */
    void updateChangeToken() {
        preferenceDataStore.put(CHANGE_TOKEN_KEY, UUID.randomUUID().toString());
    }

    /**
     * Disassociate the named user only if the named user ID is really null.
     */
    synchronized void disassociateNamedUserIfNull() {
        if (UAStringUtil.equals(getId(), null)) {
            setId(null);
        }
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
    String getLastUpdatedToken() {
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

    /**
     * Start service to clear pending named user tags.
     */
    void startClearPendingTagsService() {
        Context ctx = UAirship.getApplicationContext();
        Intent i = new Intent(ctx, PushService.class);
        i.setAction(PushService.ACTION_CLEAR_PENDING_NAMED_USER_TAGS);
        ctx.startService(i);
    }

    /**
     * Start service for named user tags update.
     */
    void startUpdateTagsService() {
        Context ctx = UAirship.getApplicationContext();
        Intent i = new Intent(ctx, PushService.class);
        i.setAction(PushService.ACTION_UPDATE_NAMED_USER_TAGS);
        ctx.startService(i);
    }

    /**
     *  Returns the pending tag group.
     *
     *  @param tagGroupKey The tag group key string.
     *  @return The pending tag groups.
     */
    Map<String, Set<String>> getPendingTagGroups(String tagGroupKey) {
        JsonValue tagGroupsJsonValue = null;
        try {
            tagGroupsJsonValue = JsonValue.parseString(preferenceDataStore.getString(tagGroupKey, null));
        } catch (JsonException e) {
            Logger.error("Unable to parse pending tag groups.", e);
            preferenceDataStore.remove(tagGroupKey);
        }

        return TagUtils.convertToTagsMap(tagGroupsJsonValue);
    }
    /**
     * Returns the pending add tag groups.
     *
     * @return The pending add tag groups.
     */
    Map<String, Set<String>> getPendingAddTagGroups() {
        return getPendingTagGroups(PENDING_ADD_TAG_GROUPS_KEY);
    }

    /**
     * Returns the pending remove tag groups.
     *
     * @return The pending remove tag groups.
     */
    Map<String, Set<String>> getPendingRemoveTagGroups() {
        return getPendingTagGroups(PENDING_REMOVE_TAG_GROUPS_KEY);
    }

    /**
     * Stores the pending add and remove tag groups.
     *
     * @param pendingAddTagGroups The pending add tag groups.
     * @param pendingRemoveTagGroups The pending remove tag groups.
     */
    void setPendingTagGroupsChanges(Map<String, Set<String>> pendingAddTagGroups, Map<String, Set<String>> pendingRemoveTagGroups) {
        preferenceDataStore.put(PENDING_ADD_TAG_GROUPS_KEY, JsonValue.wrap(pendingAddTagGroups, null));
        preferenceDataStore.put(PENDING_REMOVE_TAG_GROUPS_KEY, JsonValue.wrap(pendingRemoveTagGroups, null));
    }

}
