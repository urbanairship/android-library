/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.push;

import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.http.Response;
import com.urbanairship.job.Job;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.util.UAHttpStatusUtil;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles tag group updates.
 */
class TagGroupHandler {

    /**
     * Abstracts access to persisted pending tag group updates.
     */
    interface TagAccess {

        /**
         * Gets the pending set tag groups.
         *
         * @return The map of pending set tag groups.
         */
        Map<String, Set<String>> getPendingSetTags();

        /**
         * Gets the pending remove tag groups.
         *
         * @return The map of pending remove tag groups.
         */
        Map<String, Set<String>> getPendingRemoveTags();

        /**
         * Gets the pending add tag groups.
         *
         * @return The map of pending add tag groups.
         */
        Map<String, Set<String>> getPendingAddTags();

        /**
         * Sets the pending set tag groups.
         *
         * @param tags The pending set tag groups.
         */
        void setPendingSetTags(Map<String, Set<String>> tags);

        /**
         * Sets the pending add tag groups.
         *
         * @param tags The pending add tag groups.
         */
        void setPendingAddTags(Map<String, Set<String>> tags);

        /**
         * Sets the pending remove tag groups.
         *
         * @param tags The pending remove tag groups.
         */
        void setPendingRemoveTags(Map<String, Set<String>> tags);

        /**
         * Clears the pending set tag groups after an update.
         */
        void removePendingSetTags();

        /**
         * Clears the pending add and remove tag groups after an update.
         */
        void removePendingAddRemoveTags();
    }

    private final TagAccess tagAccess;
    private final JobDispatcher dispatcher;
    private final BaseApiClient client;

    TagGroupHandler(TagAccess tagAccess, BaseApiClient client, JobDispatcher dispatcher) {
        this.tagAccess = tagAccess;
        this.client = client;
        this.dispatcher = dispatcher;
    }

    /**
     * Handles any pending tag group changes.
     *
     * @param job The airship job.
     * @return The job result.
     */
    boolean applyTagGroupChanges(Job job) {
        Map<String, Set<String>> pendingAddTags = tagAccess.getPendingAddTags();
        Map<String, Set<String>> pendingRemoveTags = tagAccess.getPendingRemoveTags();
        Map<String, Set<String>> pendingSetTags = tagAccess.getPendingSetTags();

        Bundle setTagsBundle = job.getExtras().getBundle(TagGroupsEditor.EXTRA_SET_TAG_GROUPS);
        if (setTagsBundle != null && !setTagsBundle.isEmpty()) {
            // override current pending tags for overlapping groups
            Map<String, Set<String>> tagsToSet = new HashMap<>();
            for (String group : setTagsBundle.keySet()) {
                List<String> tags = setTagsBundle.getStringArrayList(group);

                if (tags == null) {
                    continue;
                }

                if (tagsToSet.containsKey(group)) {
                    tagsToSet.get(group).addAll(tags);
                } else {
                    tagsToSet.put(group, new HashSet<>(tags));
                }

                pendingAddTags.remove(group);
                pendingRemoveTags.remove(group);
                pendingSetTags.remove(group);
            }

            pendingSetTags.putAll(tagsToSet);
        }

        // Add tags from bundle to pendingAddTags and remove them from pendingRemoveTags.
        Bundle addTagsBundle = job.getExtras().getBundle(TagGroupsEditor.EXTRA_ADD_TAG_GROUPS);
        TagUtils.combineTagGroups(addTagsBundle, pendingAddTags, pendingRemoveTags);

        // Add tags from bundle to pendingRemoveTags and remove them from pendingAddTags.
        Bundle removeTagsBundle = job.getExtras().getBundle(TagGroupsEditor.EXTRA_REMOVE_TAG_GROUPS);
        TagUtils.combineTagGroups(removeTagsBundle, pendingRemoveTags, pendingAddTags);

        // Squash new add/remove request into pending set request.
        TagUtils.squashTags(pendingSetTags, pendingAddTags, pendingRemoveTags);
        tagAccess.setPendingAddTags(pendingAddTags);
        tagAccess.setPendingRemoveTags(pendingRemoveTags);
        tagAccess.setPendingSetTags(pendingSetTags);

        return !pendingAddTags.isEmpty() || !pendingRemoveTags.isEmpty() || !pendingSetTags.isEmpty();
    }

    /**
     * Handles performing any tag group requests if any pending tag group changes are available.
     *
     * @return The job result.
     */
    @Job.JobResult
    int updateTagGroup(String identifier) {
        Map<String, Set<String>> pendingAddTags = tagAccess.getPendingAddTags();
        Map<String, Set<String>> pendingRemoveTags = tagAccess.getPendingRemoveTags();
        Map<String, Set<String>> pendingSetTags = tagAccess.getPendingSetTags();

        // Make sure we actually have tag changes to perform
        if (pendingAddTags.isEmpty() && pendingRemoveTags.isEmpty() && pendingSetTags.isEmpty()) {
            Logger.verbose( "TagGroupHandler - Pending tag group changes empty. Skipping update.");
            return Job.JOB_FINISHED;
        }

        Response response;
        if (!pendingSetTags.isEmpty()) {
            response = client.updateTagGroups(identifier, null, null, pendingSetTags);
        } else {
            response = client.updateTagGroups(identifier, pendingAddTags, pendingRemoveTags, null);
        }

        // 5xx or no response
        if (response == null || UAHttpStatusUtil.inServerErrorRange(response.getStatus())) {
            Logger.info("TagGroupHandler - Failed to update tag groups, will retry later.");
            return Job.JOB_RETRY;
        }

        int status = response.getStatus();

        Logger.info("TagGroupHandler - Update tag groups finished with status: " + status);

        // Clear pending groups if success, forbidden, or bad request
        if (UAHttpStatusUtil.inSuccessRange(status) || status == HttpURLConnection.HTTP_FORBIDDEN || status == HttpURLConnection.HTTP_BAD_REQUEST) {
            if (!pendingSetTags.isEmpty()) {
                tagAccess.removePendingSetTags();
                Job updateJob = Job.newBuilder(ChannelJobHandler.ACTION_UPDATE_TAG_GROUPS)
                                   .setAirshipComponent(PushManager.class)
                                   .build();

                dispatcher.dispatch(updateJob);
            } else {
                tagAccess.removePendingAddRemoveTags();
            }
        }

        return Job.JOB_FINISHED;
    }
}
