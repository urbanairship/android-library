/* Copyright Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.channel.TagGroupsEditor;

import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;

/**
 * An action that adds tags.
 * <p>
 * Accepted situations: all
 * <p>
 * Accepted argument value types: A string for a single tag, a Collection of Strings for multiple tags,
 * or a JSON payload for tag groups. An example JSON payload:
 * <pre>
 * {
 *   "channel": {
 *     "channel_tag_group": ["channel_tag_1", "channel_tag_2"],
 *     "other_channel_tag_group": ["other_channel_tag_1"]
 *   },
 *   "named_user": {
 *     "named_user_tag_group": ["named_user_tag_1", "named_user_tag_2"],
 *     "other_named_user_tag_group": ["other_named_user_tag_1"]
 *   },
 *   "device": ["tag 1", "tag 2"]
 * }
 * </pre>
 * <p>
 * Result value: <code>null</code>
 * <p>
 * Default Registration Names: ^+t, add_tags_action
 * <p>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class AddTagsAction extends BaseTagsAction {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "add_tags_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^+t";

    @Override
    void applyChannelTags(@NonNull Set<String> tags) {
        Logger.info("AddTagsAction - Adding tags: %s", tags);
        getChannel().editTags().addTags(tags).apply();
    }

    @Override
    void applyChannelTagGroups(@NonNull Map<String, Set<String>> tags) {
        Logger.info("AddTagsAction - Adding channel tag groups: %s", tags);

        TagGroupsEditor tagGroupsEditor = getChannel().editTagGroups();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            tagGroupsEditor.addTags(entry.getKey(), entry.getValue());
        }

        tagGroupsEditor.apply();
    }

    @Override
    void applyNamedUserTagGroups(@NonNull Map<String, Set<String>> tags) {
        Logger.info("AddTagsAction - Adding named user tag groups: %s", tags);

        TagGroupsEditor tagGroupsEditor = UAirship.shared().getContact().editTagGroups();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            tagGroupsEditor.addTags(entry.getKey(), entry.getValue());
        }

        tagGroupsEditor.apply();
    }

    /**
     * Default {@link AddTagsAction} predicate.
     */
    public static class AddTagsPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(@NonNull ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }

}
