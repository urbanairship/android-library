/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.urbanairship.AirshipService;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.UAHttpStatusUtil;

import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles tag group intents.
 */
class TagGroupIntentHandler {

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
     * Key for storing the pending named user add tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_ADD_TAG_GROUPS_KEY";

    /**
     * Key for storing the pending named user remove tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.nameduser.PENDING_REMOVE_TAG_GROUPS_KEY";

    /**
     * Key for storing the pending channel add tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_CHANNEL_ADD_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_ADD_TAG_GROUPS";

    /**
     * Key for storing the pending channel remove tags changes in the {@link PreferenceDataStore}.
     */
    static final String PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY = "com.urbanairship.push.PENDING_REMOVE_TAG_GROUPS";

    private final PushManager pushManager;
    private final NamedUser namedUser;
    private final TagGroupsApiClient client;
    private final Context context;
    private final PreferenceDataStore dataStore;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    TagGroupIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, new TagGroupsApiClient(airship.getAirshipConfigOptions()));
    }

    @VisibleForTesting
    TagGroupIntentHandler(Context context, UAirship airship, PreferenceDataStore dataStore,
                          TagGroupsApiClient client) {

        this.context = context;
        this.dataStore = dataStore;
        this.client = client;
        this.pushManager = airship.getPushManager();
        this.namedUser = airship.getNamedUser();
    }

    /**
     * Handles {@link AirshipService} intents for {@link com.urbanairship.push.PushManager}.
     *
     * @param intent The intent.
     */
    protected void handleIntent(Intent intent) {
        switch (intent.getAction()) {
            case ACTION_UPDATE_CHANNEL_TAG_GROUPS:
            case ACTION_UPDATE_NAMED_USER_TAGS:
                onUpdateTagGroups(intent);
                break;
            case ACTION_CLEAR_PENDING_NAMED_USER_TAGS:
                onClearPendingNamedUserTags();
                break;
        }
    }

    /**
     * Update tag groups.
     *
     * @param intent The tag update intent.
     */
    private void onUpdateTagGroups(Intent intent) {

        boolean isChannelTagGroup = intent.getAction().equals(ACTION_UPDATE_CHANNEL_TAG_GROUPS);

        String pendingAddTagGroupsKey;
        String pendingRemoveTagGroupsKey;

        if (isChannelTagGroup) {
            pendingAddTagGroupsKey = PENDING_CHANNEL_ADD_TAG_GROUPS_KEY;
            pendingRemoveTagGroupsKey = PENDING_CHANNEL_REMOVE_TAG_GROUPS_KEY;
        } else {
            pendingAddTagGroupsKey = PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY;
            pendingRemoveTagGroupsKey = PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY;
        }

        Map<String, Set<String>> pendingAddTags = getPendingTagChanges(pendingAddTagGroupsKey);
        Map<String, Set<String>> pendingRemoveTags = getPendingTagChanges(pendingRemoveTagGroupsKey);

        // Add tags from bundle to pendingAddTags and remove them from pendingRemoveTags.
        Bundle addTagsBundle = intent.getBundleExtra(EXTRA_ADD_TAG_GROUPS);
        if (addTagsBundle != null) {
            combineTags(addTagsBundle, pendingAddTags, pendingRemoveTags);
            intent.removeExtra(EXTRA_ADD_TAG_GROUPS);
        }

        // Add tags from bundle to pendingRemoveTags and remove them from pendingAddTags.
        Bundle removeTagsBundle = intent.getBundleExtra(EXTRA_REMOVE_TAG_GROUPS);
        if (removeTagsBundle != null) {
            combineTags(removeTagsBundle, pendingRemoveTags, pendingAddTags);
            intent.removeExtra(EXTRA_REMOVE_TAG_GROUPS);
        }

        // Make sure we actually have tag changes to perform
        if (pendingAddTags.isEmpty() && pendingRemoveTags.isEmpty()) {
            dataStore.remove(pendingAddTagGroupsKey);
            dataStore.remove(pendingRemoveTagGroupsKey);
            return;
        }

        Response response;
        if (isChannelTagGroup) {
            String channelId = pushManager.getChannelId();
            if (channelId == null) {
                Logger.debug("Unable to update tag groups until a channel is created. Saved pending tag groups.");

                // Save pending changes
                storePendingTagChanges(pendingAddTagGroupsKey, pendingAddTags);
                storePendingTagChanges(pendingRemoveTagGroupsKey, pendingRemoveTags);

                return;
            }

            response = client.updateChannelTags(channelId, pendingAddTags, pendingRemoveTags);
        } else {
            String namedUserId = namedUser.getId();
            if (namedUserId == null) {
                Logger.verbose("Failed to update named user tags due to null named user ID. Saved pending tag groups.");

                // Save pending changes
                storePendingTagChanges(pendingAddTagGroupsKey, pendingAddTags);
                storePendingTagChanges(pendingRemoveTagGroupsKey, pendingRemoveTags);

                return;
            }

            response = client.updateNamedUserTags(namedUser.getId(), pendingAddTags, pendingRemoveTags);
        }


        // 5xx or no response
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.info("Failed to update tag groups, will retry. Saved pending tag groups.");

            // Save pending changes
            storePendingTagChanges(pendingAddTagGroupsKey, pendingAddTags);
            storePendingTagChanges(pendingRemoveTagGroupsKey, pendingRemoveTags);

            // Retry later
            AirshipService.retryServiceIntent(context, intent);

            return;
        }

        // 2xx
        if (UAHttpStatusUtil.inSuccessRange(response.getStatus())) {
            Logger.info("Update tag groups succeeded with status: " + response.getStatus());
            logTagGroupResponseIssues(response.getResponseBody());

            // Clear pending
            dataStore.remove(pendingAddTagGroupsKey);
            dataStore.remove(pendingRemoveTagGroupsKey);

            return;
        }

        // Other failure
        Logger.info("Update tag groups failed with status: " + response.getStatus());
        logTagGroupResponseIssues(response.getResponseBody());

        if (response.getStatus() == HttpURLConnection.HTTP_FORBIDDEN || response.getStatus() == HttpURLConnection.HTTP_BAD_REQUEST) {
            // Clear pending
            dataStore.remove(pendingAddTagGroupsKey);
            dataStore.remove(pendingRemoveTagGroupsKey);
        } else {
            // Save pending
            storePendingTagChanges(pendingAddTagGroupsKey, pendingAddTags);
            storePendingTagChanges(pendingRemoveTagGroupsKey, pendingRemoveTags);
        }

    }

    /**
     * Clear pending named user tags.
     */
    private void onClearPendingNamedUserTags() {
        dataStore.remove(PENDING_NAMED_USER_ADD_TAG_GROUPS_KEY);
        dataStore.remove(PENDING_NAMED_USER_REMOVE_TAG_GROUPS_KEY);
    }

    /**
     * Combine the tags from bundle with the pending tags.
     *
     * @param tagsBundle The tags bundle.
     * @param tagsToAdd The pending tags to add tags to.
     * @param tagsToRemove The pending tags to remove tags from.
     */
    private void combineTags(Bundle tagsBundle, Map<String, Set<String>> tagsToAdd, Map<String, Set<String>> tagsToRemove) {
        for (String group : tagsBundle.keySet()) {
            List<String> tags = tagsBundle.getStringArrayList(group);

            if (tags == null) {
                continue;
            }

            // Add tags to tagsToAdd.
            if (tagsToAdd.containsKey(group)) {
                tagsToAdd.get(group).addAll(tags);
            } else {
                tagsToAdd.put(group, new HashSet<>(tags));
            }

            // Remove tags from tagsToRemove.
            if (tagsToRemove.containsKey(group)) {
                tagsToRemove.get(group).removeAll(tags);
            }
        }
    }

    /**
     * Log the response warnings and errors if they exist in the response body.
     *
     * @param responseBody The response body string.
     */
    private void logTagGroupResponseIssues(String responseBody) {
        if (responseBody == null) {
            return;
        }

        JsonValue responseJson;
        try {
            responseJson = JsonValue.parseString(responseBody);
        } catch (JsonException e) {
            Logger.error("Unable to parse tag group response", e);
            return;
        }

        if (responseJson.isJsonMap()) {
            // Check for any warnings in the response and log them if they exist.
            if (responseJson.getMap().containsKey("warnings")) {
                for (JsonValue warning : responseJson.getMap().get("warnings").getList()) {
                    Logger.info("Tag Groups warnings: " + warning);
                }
            }

            // Check for any errors in the response and log them if they exist.
            if (responseJson.getMap().containsKey("error")) {
                Logger.info("Tag Groups error: " + responseJson.getMap().get("error"));
            }
        }
    }

    /**
     * Stores pending tag groups changes in the {@link PreferenceDataStore}.
     *
     * @param tagGroupKey The data store key.
     * @param tagGroupChanges The pending tag groups.
     */
    private void storePendingTagChanges(@NonNull String tagGroupKey, @NonNull Map<String, Set<String>> tagGroupChanges) {
        dataStore.put(tagGroupKey, JsonValue.wrapOpt(tagGroupChanges));
    }

    /**
     * Returns the pending tag group changes for the given {@link PreferenceDataStore} key.
     *
     * @param tagGroupKey The tag group key string.
     * @return The pending tag groups.
     */
    @NonNull
    private Map<String, Set<String>> getPendingTagChanges(String tagGroupKey) {
        JsonValue tagGroupsJsonValue = null;
        try {
            tagGroupsJsonValue = JsonValue.parseString(dataStore.getString(tagGroupKey, null));
        } catch (JsonException e) {
            Logger.error("Unable to parse pending tag groups.", e);
            dataStore.remove(tagGroupKey);
        }

        return TagUtils.convertToTagsMap(tagGroupsJsonValue);
    }

}
