/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.base.Supplier;
import com.urbanairship.channel.SubscriptionListEditor;
import com.urbanairship.contacts.Scope;
import com.urbanairship.contacts.ScopedSubscriptionListEditor;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

/**
 * Action for subscribing/unsubscribing to lists.
 * <p>
 * Accepted situations: all
 * <p>
 * Accepted argument value types: A JSON Payload containing a type, a list, an action and a scope.
 * An example JSON Payload :
 * [
 *    {
 *       "type": "contact",
 *       "action": "subscribe",
 *       "list": "mylist",
 *       "scope": "app"
 *    },
 *    {
 *       "type": "channel",
 *       "action": "unsubscribe",
 *       "list": "thelist"
 *    }
 * ]
 * <p>
 * Result value: The payload used.
 * <p>
 * Default Registration Names: ^sla, subscription_list_action
 * <p>
 * Default Registration Predicate: none
 */
public class SubscriptionListAction extends Action {

    /**
     * JSON key for subscription type.
     */
    @NonNull
    private static final String TYPE_KEY = "type";

    /**
     * JSON key for list name to subscribe for.
     */
    @NonNull
    private static final String LIST_KEY = "list";

    /**
     * JSON key for action (subscribe or unsubscribe).
     */
    @NonNull
    private static final String ACTION_KEY = "action";

    /**
     * JSON key for scope (app, web, email or SMS).
     */
    @NonNull
    private static final String SCOPE_KEY = "scope";

    /**
     * JSON key for edits list.
     */
    @NonNull
    private static final String EDITS_KEY = "edits";

    private static final String SUBSCRIBE_KEY = "subscribe";
    private static final String UNSUBSCRIBE_KEY = "unsubscribe";

    private static final String CHANNEL_KEY = "channel";
    private static final String CONTACT_KEY = "contact";

    /**
     * Default registry name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_NAME = "subscription_list_action";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String DEFAULT_REGISTRY_SHORT_NAME = "^sla";

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        if (arguments.getValue().getMap() != null) {
            JsonValue edits = arguments.getValue().getMap().opt(EDITS_KEY);
            Logger.debug(edits.toString());
        }

        SubscriptionListEditor channelEditor = UAirship.shared().getChannel().editSubscriptionLists();
        ScopedSubscriptionListEditor contactEditor = UAirship.shared().getContact().editSubscriptionLists();
        JsonList edits = arguments.getValue().toJsonValue().getList();

        for (JsonValue edit : edits) {
            JsonMap editMap = edit.getMap();
            String listId = editMap.opt(LIST_KEY).getString();
            if (listId != null) {
                if (editMap.opt(TYPE_KEY).getString().equals(CHANNEL_KEY)) {
                    if (editMap.opt(ACTION_KEY).getString().equals(SUBSCRIBE_KEY)) {
                        channelEditor.subscribe(listId);
                    } else if (editMap.opt(ACTION_KEY).getString().equals(UNSUBSCRIBE_KEY)) {
                        channelEditor.unsubscribe(listId);
                    }
                } else if (editMap.opt(TYPE_KEY).getString().equals(CONTACT_KEY)) {
                    try {
                        Scope scope = Scope.fromJson(editMap.opt(SCOPE_KEY));
                        if (editMap.opt(ACTION_KEY).getString().equals(SUBSCRIBE_KEY)) {
                            contactEditor.subscribe(listId, scope);
                        } else if (editMap.opt(ACTION_KEY).getString().equals(UNSUBSCRIBE_KEY)) {
                            contactEditor.unsubscribe(listId, scope);
                        }
                    } catch (JsonException e) {
                        Logger.error("Scope error : " + e.getMessage());
                        e.printStackTrace();
                        return ActionResult.newEmptyResult();
                    }
                }
            } else {
                Logger.error("Error : the List ID is missing");
                return ActionResult.newEmptyResult();
            }
        }

        channelEditor.apply();
        contactEditor.apply();

        return ActionResult.newResult(arguments.getValue());
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        if (arguments.getValue().isNull()) {
            return false;
        }

        if (arguments.getValue().toJsonValue().getList() == null) {
            return false;
        }

        return true;
    }



}
