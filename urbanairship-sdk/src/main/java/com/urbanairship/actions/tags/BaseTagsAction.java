/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract tag action class.
 */
public abstract class BaseTagsAction extends Action {

    /**
     * Gets the push manager
     *
     * @return A push manager instance
     */
    protected PushManager getPushManager() {
        return UAirship.shared().getPushManager();
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        return getTags(arguments) != null;
    }

    /**
     * Parses the arguments for a set of tags
     *
     * @param arguments The action arguments
     * @return A set of tags from the arguments, or null if no tags
     * could be parsed from the arguments
     */
    protected Set<String> getTags(@NonNull ActionArguments arguments) {
        if (arguments.getValue().isNull()) {
            return null;
        }

        if (arguments.getValue().getString() != null) {
            Set<String> tags = new HashSet<>();
            tags.add(String.valueOf(arguments.getValue().getString()));
            return tags;
        }

        if (arguments.getValue().getList() != null) {
            Set<String> tags = new HashSet<>();

            for (JsonValue tag : arguments.getValue().getList()) {
                if (tag.getString() != null) {
                    tags.add(tag.getString());
                }
            }

            return tags;
        }

        return null;
    }
}
