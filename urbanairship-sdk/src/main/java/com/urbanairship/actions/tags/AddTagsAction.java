/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.Logger;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;

import java.util.Set;

/**
 * An action that adds tags.
 * <p/>
 * Accepted situations: all
 * <p/>
 * Accepted argument value types: String for a single tag or Collection of Strings for multiple tags.
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
    public ActionResult perform(@NonNull ActionArguments arguments) {
        Set<String> tags = getTags(arguments);
        Logger.info("AddTagsAction - Adding tags: " + tags);

        tags.addAll(getPushManager().getTags());
        getPushManager().setTags(tags);

        return ActionResult.newEmptyResult();
    }
}
