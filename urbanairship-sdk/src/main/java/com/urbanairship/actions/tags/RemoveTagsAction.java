/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;

import java.util.Set;

/**
 * An action that removes tags.
 * <p/>
 * Accepted situations: all
 * <p/>
 * Accepted argument value types: String for a single tag or Collection of Strings for multiple tags.
 * <p/>
 * Result value: null
 * <p/>
 * Default Registration Names: ^-t, remove_tags_action
 * <p/>
 * Default Registration Predicate: Rejects SITUATION_PUSH_RECEIVED
 */
public class RemoveTagsAction extends BaseTagsAction {

    /**
     * Default registry name
     */
    public static final String DEFAULT_REGISTRY_NAME = "remove_tags_action";

    /**
     * Default registry short name
     */
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^-t";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Set<String> tags = getTags(arguments);
        Logger.info("RemoveTagsAction - Removing tags: " + tags);

        Set<String> currentTags = getPushManager().getTags();
        currentTags.removeAll(tags);

        getPushManager().setTags(currentTags);

        return ActionResult.newEmptyResult();
    }
}
