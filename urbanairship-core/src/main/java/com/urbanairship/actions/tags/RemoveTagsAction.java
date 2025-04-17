/* Copyright Airship and Contributors */

package com.urbanairship.actions.tags;

import com.urbanairship.UALog;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRegistry;
import com.urbanairship.channel.TagGroupsEditor;

import java.util.Map;
import java.util.Set;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

/**
 * An action that removes tags.
 * <p>
 * Accepted situations: all
 * <p>
 * Accepted argument value types: A string for a single tag, A Collection of Strings for multiple tags,
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
 * Result value: null
 * <p>
 * Default Registration Names: ^-t, remove_tags_action
 * <p>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class RemoveTagsAction extends BaseTagsAction {

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "remove_tags_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^-t";

    @Override
    @CallSuper
    public void applyChannelTags(@NonNull Set<String> tags) {
        UALog.i("RemoveTagsAction - Removing tags: %s", tags);
        getChannel().editTags().removeTags(tags).apply();
    }

    @Override
    @CallSuper
    public void applyChannelTagGroups(@NonNull Map<String, Set<String>> tags) {
        UALog.i("RemoveTagsAction - Removing channel tag groups: %s", tags);
        TagGroupsEditor tagGroupsEditor = getChannel().editTagGroups();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            tagGroupsEditor.removeTags(entry.getKey(), entry.getValue());
        }

        tagGroupsEditor.apply();
    }

    @Override
    @CallSuper
    public void applyContactTagGroups(@NonNull Map<String, Set<String>> tags) {
        UALog.i("RemoveTagsAction - Removing contact tag groups: %s", tags);

        TagGroupsEditor tagGroupsEditor = UAirship.shared().getContact().editTagGroups();
        for (Map.Entry<String, Set<String>> entry : tags.entrySet()) {
            tagGroupsEditor.removeTags(entry.getKey(), entry.getValue());
        }

        tagGroupsEditor.apply();
    }

    /**
     * Default {@link RemoveTagsPredicate} predicate.
     */
    public static class RemoveTagsPredicate implements ActionRegistry.Predicate {

        @Override
        public boolean apply(@NonNull ActionArguments arguments) {
            return Action.SITUATION_PUSH_RECEIVED != arguments.getSituation();
        }

    }

}
