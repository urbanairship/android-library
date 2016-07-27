/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.AirshipComponent;
import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.util.UAStringUtil;

import java.util.UUID;

/**
 * The named user is an alternate method of identifying the device. Once a named
 * user is associated to the device, it can be used to send push notifications
 * to the device.
 */
public class NamedUser extends AirshipComponent {

    /**
     * The change token tracks the start of setting the named user ID.
     */
    private static final String CHANGE_TOKEN_KEY = "com.urbanairship.nameduser.CHANGE_TOKEN_KEY";

    /**
     * The named user ID.
     */
    private static final String NAMED_USER_ID_KEY = "com.urbanairship.nameduser.NAMED_USER_ID_KEY";

    /**
     * The maximum length of the named user ID string.
     */
    private static final int MAX_NAMED_USER_ID_LENGTH = 128;

    private final PreferenceDataStore preferenceDataStore;
    private final Context context;
    private final Object lock = new Object();
    private NamedUserIntentHandler namedUserIntentHandler;

    /**
     * Creates a NamedUser.
     *
     * @param context The application context.
     * @param preferenceDataStore The preferences data store.
     */
    public NamedUser(@NonNull Context context, @NonNull PreferenceDataStore preferenceDataStore) {
        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
    }

    @Override
    protected void init() {
        // Start named user update
        startUpdateService();

        // Update named user tags if we have a named user
        if (getId() != null) {
            startUpdateTagsService();
        }
    }

    @Override
    protected boolean acceptsIntentAction(UAirship airship, @NonNull String action) {
        switch (action) {
            case NamedUserIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES:
            case NamedUserIntentHandler.ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
            case NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS:
            case NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER:
                return true;
        }

        return false;
    }

    @Override
    protected void onHandleIntent(@NonNull UAirship airship, @NonNull Intent intent) {

        switch (intent.getAction()) {
            case NamedUserIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES:
            case NamedUserIntentHandler.ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
            case NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS:
            case NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER:
                if (namedUserIntentHandler == null) {
                    namedUserIntentHandler = new NamedUserIntentHandler(context, airship, preferenceDataStore);
                }
                namedUserIntentHandler.handleIntent(intent);
                break;
        }
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
    public void setId(@Nullable String namedUserId) {
        String id = null;
        if (namedUserId != null) {
            id = namedUserId.trim();
            if (UAStringUtil.isEmpty(id) || id.length() > MAX_NAMED_USER_ID_LENGTH) {
                Logger.error("Failed to set named user ID. " +
                        "The named user ID must be greater than 0 and less than 129 characters.");
                return;
            }
        }

        synchronized (lock) {
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
    }

    /**
     * Edit the named user tags.
     *
     * @return The TagGroupsEditor.
     */
    public TagGroupsEditor editTagGroups() {
        return new TagGroupsEditor(NamedUserIntentHandler.ACTION_APPLY_TAG_GROUP_CHANGES);
    }

    /**
     * Gets the named user ID change token.
     *
     * @return The named user ID change token.
     */
    @Nullable
    String getChangeToken() {
        return preferenceDataStore.getString(CHANGE_TOKEN_KEY, null);
    }

    /**
     * Modify the change token to force an update.
     */
    private void updateChangeToken() {
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
     * Start service for named user update.
     */
    void startUpdateService() {
        Intent i = new Intent(context, AirshipService.class)
                .setAction(NamedUserIntentHandler.ACTION_UPDATE_NAMED_USER);

        context.startService(i);
    }

    /**
     * Start service to clear pending named user tags.
     */
    void startClearPendingTagsService() {
        Intent i = new Intent(context, AirshipService.class)
                .setAction(NamedUserIntentHandler.ACTION_CLEAR_PENDING_NAMED_USER_TAGS);

        context.startService(i);
    }

    /**
     * Start service for named user tags update.
     */
    void startUpdateTagsService() {
        Intent i = new Intent(context, AirshipService.class)
                .setAction(NamedUserIntentHandler.ACTION_UPDATE_TAG_GROUPS);

        context.startService(i);
    }
}
