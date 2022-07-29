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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.ObjectsCompat;

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
 * Default Registration Names: ^sla, ^sl, subscription_list_action, edit_subscription_list_action
 * <p>
 * Default Registration Predicate: none
 */
public class SubscriptionListAction extends Action {

    // Arg keys
    private static final String TYPE_KEY = "type";
    private static final String LIST_KEY = "list";
    private static final String ACTION_KEY = "action";
    private static final String SCOPE_KEY = "scope";
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

    /**
     * Default registry short name
     */
    @NonNull
    public static final String ALT_DEFAULT_REGISTRY_SHORT_NAME = "^sl";

    /**
     * Default registry short name
     */
    @NonNull
    public static final String ALT_DEFAULT_REGISTRY_NAME = "edit_subscription_list_action";

    private final Supplier<SubscriptionListEditor> channelEditorSupplier;
    private final Supplier<ScopedSubscriptionListEditor> contactEditorSupplier;

    /**
     * Default constructor.
     */
    public SubscriptionListAction() {
        this(
                () -> UAirship.shared().getChannel().editSubscriptionLists(),
                () -> UAirship.shared().getContact().editSubscriptionLists()
        );
    }

    @VisibleForTesting
    SubscriptionListAction(@NonNull Supplier<SubscriptionListEditor> channelEditorSupplier,
                           @NonNull Supplier<ScopedSubscriptionListEditor> contactEditorSupplier) {
        this.channelEditorSupplier = channelEditorSupplier;
        this.contactEditorSupplier = contactEditorSupplier;
    }

    @NonNull
    @Override
    public ActionResult perform(@NonNull ActionArguments arguments) {
        SubscriptionListEditor channelEditor = ObjectsCompat.requireNonNull(channelEditorSupplier.get());
        ScopedSubscriptionListEditor contactEditor = ObjectsCompat.requireNonNull(contactEditorSupplier.get());
        JsonList operations = arguments.getValue().toJsonValue().optList();

        for (JsonValue operation : operations) {
            try {
                JsonMap map = operation.requireMap();
                String listId = map.require(LIST_KEY).requireString();
                String type = map.require(TYPE_KEY).requireString();
                String action = map.require(ACTION_KEY).requireString();

                switch (type) {
                    case CHANNEL_KEY:
                        applyChannelOperation(channelEditor, listId, action);
                        break;
                    case CONTACT_KEY:
                        Scope scope = Scope.fromJson(map.require(SCOPE_KEY));
                        applyContactOperation(contactEditor, listId, action, scope);
                        break;
                }
            } catch (JsonException e) {
                Logger.error(e, "Invalid argument");
                return ActionResult.newErrorResult(e);
            }
        }

        channelEditor.apply();
        contactEditor.apply();
        return ActionResult.newResult(arguments.getValue());
    }

    private void applyContactOperation(@NonNull ScopedSubscriptionListEditor editor,
                                       @NonNull String listId,
                                       @NonNull String action,
                                       @NonNull Scope scope) throws JsonException {
        switch (action) {
            case SUBSCRIBE_KEY:
                editor.subscribe(listId, scope);
                break;
            case UNSUBSCRIBE_KEY:
                editor.unsubscribe(listId, scope);
                break;
            default:
                throw new JsonException("Invalid action: " + action);
        }
    }

    private void applyChannelOperation(@NonNull SubscriptionListEditor editor,
                                       @NonNull String listId,
                                       @NonNull String action) throws JsonException {

        switch (action) {
            case SUBSCRIBE_KEY:
                editor.subscribe(listId);
                break;
            case UNSUBSCRIBE_KEY:
                editor.unsubscribe(listId);
                break;
            default:
                throw new JsonException("Invalid action: " + action);
        }
    }

    @Override
    public boolean acceptsArguments(@NonNull ActionArguments arguments) {
        return !arguments.getValue().isNull() && arguments.getSituation() != SITUATION_PUSH_RECEIVED;
    }

}
