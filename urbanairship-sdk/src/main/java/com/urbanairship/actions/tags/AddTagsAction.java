/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.push.TagGroupsEditor;

import java.util.Map;
import java.util.Set;

/**
 * An action that adds tags.
 * <p/>
 * Accepted situations: all
 * <p/>
 * Accepted argument value types: A string for a single tag, a Collection of Strings for multiple tags,
 * or a JSON payload for tag groups. An example JSON payload:
 * {
 *     "channel": {
 *         "channel_tag_group": ["channel_tag_1", "channel_tag_2"],
 *         "other_channel_tag_group": ["other_channel_tag_1"]
 *     },
 *     "named_user": {
 *         "named_user_tag_group": ["named_user_tag_1", "named_user_tag_2"],
 *         "other_named_user_tag_group": ["other_named_user_tag_1"]
 *     }
 * }
 * <p/>
 * Result value: <code>null</code>
 * <p/>
 * Default Registration Names: ^+t, add_tags_action
 * <p/>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class AddTagsAction extends BaseTagsAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "add_tags_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^+t";

    @Override
    void applyChannelTags(Set<String> tags) {
        Logger.info("AddTagsAction - Adding tags: " + tags);
        tags.addAll(getPushManager().getTags());
        getPushManager().setTags(tags);
    }

    @Override
    void applyChannelTagGroups(Map<String, Set<String>> tags) {
        Logger.info("AddTagsAction - Adding channel tag groups: " + tags);

        TagGroupsEditor tagGroupsEditor = getPushManager().editTagGroups();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            tagGroupsEditor.addTags(entry.getKey(), entry.getValue());
        }

        tagGroupsEditor.apply();
    }

    @Override
    void applyNamedUserTagGroups(Map<String, Set<String>> tags) {
        Logger.info("AddTagsAction - Adding named user tag groups: " + tags);

        TagGroupsEditor tagGroupsEditor = UAirship.shared().getNamedUser().editTagGroups();
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
        public boolean apply(ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }
}
