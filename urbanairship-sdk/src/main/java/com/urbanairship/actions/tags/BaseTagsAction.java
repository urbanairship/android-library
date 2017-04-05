/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions.tags;

import android.support.annotation.NonNull;

import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushManager;
import com.urbanairship.util.UAStringUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract tag action class.
 */
abstract class BaseTagsAction extends Action {

    /**
     * JSON key for channel tag group changes.
     */
    private static final String CHANNEL_KEY = "channel";

    /**
     * JSON key for named user tag group changes.
     */
    private static final String NAMED_USER_KEY = "named_user";

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
        if (arguments.getValue().isNull()) {
            return false;
        }

        if (arguments.getValue().getString() != null) {
            return true;
        }

        if (arguments.getValue().getList() != null) {
            return true;
        }

        if (arguments.getValue().getMap() != null) {
            return true;
        }

        return false;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {

        if (arguments.getValue().getString() != null) {
            Set<String> tags = new HashSet<>();
            tags.add(String.valueOf(arguments.getValue().getString()));
            applyChannelTags(tags);
        }

        if (arguments.getValue().getList() != null) {
            Set<String> tags = new HashSet<>();
            for (JsonValue tag : arguments.getValue().getList()) {
                if (tag.getString() != null) {
                    tags.add(tag.getString());
                }
            }

            applyChannelTags(tags);
        }

        if (arguments.getValue().getMap() != null) {
            Map<String, Set<String>> tagsMap = new HashMap<>();
            for (Map.Entry<String, JsonValue> entry : arguments.getValue().getMap().opt(CHANNEL_KEY).optMap().getMap().entrySet()) {
                String group = entry.getKey();
                Set<String> tags = new HashSet<>();
                for (JsonValue jsonValue : entry.getValue().optList().getList()) {
                    tags.add(jsonValue.getString());
                }

                if (!UAStringUtil.isEmpty(group) && !tags.isEmpty()) {
                    tagsMap.put(group, tags);
                }
            }

            if (!tagsMap.isEmpty()) {
                applyChannelTagGroups(tagsMap);
            }

            tagsMap = new HashMap<>();
            for (Map.Entry<String, JsonValue> entry :  arguments.getValue().getMap().opt(NAMED_USER_KEY).optMap().getMap().entrySet()) {
                String group = entry.getKey();
                Set<String> tags = new HashSet<>();
                for (JsonValue jsonValue : entry.getValue().optList().getList()) {
                    tags.add(jsonValue.getString());
                }

                if (!UAStringUtil.isEmpty(group) && !tags.isEmpty()) {
                    tagsMap.put(group, tags);
                }
            }

            if (!tagsMap.isEmpty()) {
                applyNamedUserTagGroups(tagsMap);
            }
        }

        return ActionResult.newEmptyResult();
    }

    /**
     * Applies tag updates.
     *
     * @param tags The set of tags.
     */
    abstract void applyChannelTags(Set<String> tags);

    /**
     * Applies channel tag group updates.
     *
     * @param tags The map of tag groups.
     */
    abstract void applyChannelTagGroups(Map<String, Set<String>> tags);

    /**
     * Applies named user tag group updates.
     *
     * @param tags The map of tag groups.
     */
    abstract void applyNamedUserTagGroups(Map<String, Set<String>> tags);
}
