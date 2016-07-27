/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;
import com.urbanairship.util.UAStringUtil;

import java.net.HttpURLConnection;
import java.util.Map;
import java.util.Set;


/**
 * Intent handler for the NamedUser.
 */
class NamedUserIntentHandler {

    /**
     * Action to update pending named user tag groups.
     */
    static final String ACTION_APPLY_TAG_GROUP_CHANGES = "com.urbanairship.nameduser.ACTION_APPLY_TAG_GROUP_CHANGES";

    /**
     * Action to perform update request for pending named user tag group changes.
     */
    static final String ACTION_UPDATE_TAG_GROUPS = "com.urbanairship.nameduser.ACTION_UPDATE_TAG_GROUPS";

    /**
     * Key for storing the pending named user add tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_ADD_TAG_GROUPS_KEY";

    /**
     * Key for storing the pending named user remove tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_REMOVE_TAG_GROUPS_KEY";


    /**
     * Action to update named user association or disassociation.
     */
    static final String ACTION_UPDATE_NAMED_USER = "com.urbanairship.push.ACTION_UPDATE_NAMED_USER";

    /**
     * Key for storing the {@link NamedUser#getChangeToken()} in the {@link PreferenceDataStore} from the
     * last time the named user was updated.
     */
    static final String LAST_UPDATED_TOKEN_KEY = "com.urbanairship.nameduser.LAST_UPDATED_TOKEN_KEY";

    /**
     * Action to clear the pending named user tags.
     */
    static final String ACTION_CLEAR_PENDING_NAMED_USER_TAGS = "com.urbanairship.nameduser.ACTION_CLEAR_PENDING_NAMED_USER_TAGS";

    private final NamedUserApiClient client;

    private final NamedUser namedUser;
    private final PushManager pushManager;
    private final Context context;
    private final PreferenceDataStore dataStore;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    NamedUserIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, new NamedUserApiClient(airship.getPlatformType(), airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    NamedUserIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore, NamedUserApiClient client) {
        this.context = context;
        this.dataStore = dataStore;
        this.client = client;
        this.namedUser = airship.getNamedUser();
        this.pushManager = airship.getPushManager();
    }

    /**
     * Handles {@link AirshipService} intents for {@link com.urbanairship.push.PushManager}.
     *
     * @param intent The intent.
     */
    protected void handleIntent(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_UPDATE_NAMED_USER:
                onUpdateNamedUser(intent);
                break;

            case ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
                onClearTagGroups();
                break;

            case ACTION_APPLY_TAG_GROUP_CHANGES:
                onApplyTagGroupChanges(intent);
                break;


            case ACTION_UPDATE_TAG_GROUPS:
                onUpdateTagGroup(intent);
                break;
        }
    }


    /**
     * Handles associate/disassociate updates.
     *
     * @param intent The update intent.
     */
    private void onUpdateNamedUser(Intent intent) {
        String currentId = namedUser.getId();
        String changeToken = namedUser.getChangeToken();
        String lastUpdatedToken = dataStore.getString(LAST_UPDATED_TOKEN_KEY, null);
        String channelId = pushManager.getChannelId();

        if (changeToken == null && lastUpdatedToken == null) {
            // Skip since no one has set the named user ID. Usually from a new or re-install.
            return;
        }

        if (changeToken != null && changeToken.equals(lastUpdatedToken)) {
            // Skip since no change has occurred (token remain the same).
            Logger.debug("NamedUserIntentHandler - Named user already updated. Skipping.");
            return;
        }

        if (UAStringUtil.isEmpty(channelId)) {
            Logger.info("The channel ID does not exist. Will retry when channel ID is available.");
            return;
        }

        Response response;

        if (currentId == null) {
            // When currentId is null, disassociate the current named user ID.
            response = client.disassociate(channelId);
        } else {
            // When currentId is non-null, associate the currentId.
            response = client.associate(currentId, channelId);
        }

        // 5xx
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            // Server error occurred, so retry later.
            Logger.info("Update named user failed, will retry.");
            AirshipService.retryServiceIntent(context, intent);
            return;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update named user succeeded with status: " + response.getStatus());
            dataStore.put(LAST_UPDATED_TOKEN_KEY, changeToken);
            namedUser.startUpdateTagsService();
            return;
        }

        // 403
        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN) {
            Logger.info("Update named user failed with status: " + response.getStatus() +
                    " This action is not allowed when the app is in server-only mode.");
            return;
        }

        // 4xx
        Logger.info("Update named user failed with status: " + response.getStatus());
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @param intent The update intent
     */
    private void onUpdateTagGroup(Intent intent) {
        String namedUserId = namedUser.getId();
        if (namedUserId == null) {
            Logger.verbose("Failed to update named user tags due to null named user ID.");
            return;
        }

        Map<String, Set<String>> pendingAddTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY));
        Map<String, Set<String>> pendingRemoveTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY));

        // Make sure we actually have tag changes to perform
        if (pendingAddTags.isEmpty() && pendingRemoveTags.isEmpty()) {
            Logger.verbose("Named user pending tag group changes empty. Skipping update.");
            return;
        }

        Response response = client.updateTagGroups(namedUser.getId(), pendingAddTags, pendingRemoveTags);

        // 5xx or no response
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.info("Failed to update tag groups, will retry. Saved pending tag groups.");

            // Retry later
            AirshipService.retryServiceIntent(context, intent);

            return;
        }

        int status = response.getStatus();

        Logger.info("Update named user tag groups finished with status: " + status);

        // Clear pending groups if success, forbidden, or bad request
        if (UAHttpStatusUtil.inSuccessRange(status) || status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_BAD_REQUEST) {
            onClearTagGroups();
        }
    }

    /**
     * Handles any pending tag group changes.
     *
     * @param intent The tag group intent.
     */
    private void onApplyTagGroupChanges(Intent intent) {
        Map<String, Set<String>> pendingAddTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY));
        Map<String, Set<String>> pendingRemoveTags = TagUtils.convertToTagsMap(dataStore.getJsonValue(PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY));

        // Add tags from bundle to pendingAddTags and remove them from pendingRemoveTags.
        Bundle addTagsBundle = intent.getBundleExtra(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS);
        TagUtils.combineTagGroups(addTagsBundle, pendingAddTags, pendingRemoveTags);

        // Add tags from bundle to pendingRemoveTags and remove them from pendingAddTags.
        Bundle removeTagsBundle = intent.getBundleExtra(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS);
        TagUtils.combineTagGroups(removeTagsBundle, pendingRemoveTags, pendingAddTags);

        dataStore.put(PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY, JsonValue.wrapOpt(pendingAddTags));
        dataStore.put(PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY, JsonValue.wrapOpt(pendingRemoveTags));

        // Make sure we actually have tag changes to perform
        if (namedUser.getId() != null && (!pendingAddTags.isEmpty() || !pendingRemoveTags.isEmpty())) {
            Intent updateIntent = new Intent(context, AirshipService.class)
                    .setAction(ACTION_UPDATE_TAG_GROUPS);

            context.startService(updateIntent);
        }
    }

    /**
     * Handles clearing pending tag groups.
     */
    private void onClearTagGroups() {
        dataStore.remove(PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY);
        dataStore.remove(PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY);
    }
}
