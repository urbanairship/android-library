/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.Logger;
import com.urbanairship.channel.SubscriptionListMutation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

/**
 * Defines a scoped subscription list mutation.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ScopedSubscriptionListMutation implements JsonSerializable {

    private static final String KEY_ACTION = "action";
    private static final String KEY_LIST_ID = "list_id";
    private static final String KEY_TIMESTAMP = "timestamp";
    private static final String KEY_SCOPE = "scope";

    /**
     * Subscribe action.
     */
    public static final String ACTION_SUBSCRIBE = "subscribe";

    /**
     * Unsubscribe action.
     */
    public static final String ACTION_UNSUBSCRIBE = "unsubscribe";

    @NonNull private final String action;
    @NonNull private final String listId;
    @NonNull private final Scope scope;
    @Nullable private final String timestamp;

    ScopedSubscriptionListMutation(@NonNull String action,
                                   @NonNull String listId,
                                   @Nullable Scope scope,
                                   @Nullable String timestamp) {
        this.action = action;
        this.listId = listId;
        this.scope = scope;
        this.timestamp = timestamp;
    }

    @NonNull
    public static ScopedSubscriptionListMutation newSubscribeMutation(@NonNull String listId, @NonNull Scope scope, long timestamp) {
        return new ScopedSubscriptionListMutation(ACTION_SUBSCRIBE, listId, scope, DateUtils.createIso8601TimeStamp(timestamp));
    }

    @NonNull
    public static ScopedSubscriptionListMutation newUnsubscribeMutation(@NonNull String listId,  @NonNull Scope scope, long timestamp) {
        return new ScopedSubscriptionListMutation(ACTION_UNSUBSCRIBE, listId, scope, DateUtils.createIso8601TimeStamp(timestamp));
    }

    @NonNull
    public static ScopedSubscriptionListMutation fromJsonValue(@NonNull JsonValue input) throws JsonException {
        JsonMap json = input.optMap();

        String action = json.opt(KEY_ACTION).getString();
        String listId = json.opt(KEY_LIST_ID).getString();
        String timestamp = json.opt(KEY_TIMESTAMP).getString();
        Scope scope = Scope.fromJson(json.opt(KEY_SCOPE));

        if (action == null || listId == null) {
            throw new JsonException("Invalid subscription list mutation: " + json);
        }

        return new ScopedSubscriptionListMutation(action, listId, scope, timestamp);
    }

    @NonNull
    public static List<ScopedSubscriptionListMutation> fromJsonList(@NonNull JsonList jsonList) {
        List<ScopedSubscriptionListMutation> mutations = new ArrayList<>();

        for (JsonValue value : jsonList) {
            try {
                mutations.add(fromJsonValue(value));
            } catch (JsonException e) {
                Logger.error(e, "Invalid subscription list mutation!");
            }
        }

        return mutations;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(KEY_ACTION, action)
                      .put(KEY_LIST_ID, listId)
                      .put(KEY_SCOPE, scope)
                      .put(KEY_TIMESTAMP, timestamp)
                      .build()
                      .toJsonValue();
    }

    @NonNull
    public String getAction() {
        return action;
    }

    @NonNull
    public String getListId() {
        return listId;
    }

    @Nullable
    public String getTimestamp() {
        return timestamp;
    }

    @NonNull
    public Scope getScope() {
        return scope;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopedSubscriptionListMutation that = (ScopedSubscriptionListMutation) o;
        return ObjectsCompat.equals(action, that.action) &&
                ObjectsCompat.equals(listId, that.listId) &&
                ObjectsCompat.equals(scope, that.scope) &&
                ObjectsCompat.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(action, listId, timestamp, scope);
    }

    @Override
    public String toString() {
        return "ScopedSubscriptionListMutation{" +
                "action='" + action + '\'' +
                ", listId='" + listId + '\'' +
                ", scope=" + scope +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }

    /**
     * Collapses a collection of mutations into a single mutation payload.
     *
     * @param mutations a list of subscription list mutations to collapse.
     * @return A collapsed {@code SubscriptionListMutation} object.
     */
    public static List<ScopedSubscriptionListMutation> collapseMutations(List<ScopedSubscriptionListMutation> mutations) {
        List<ScopedSubscriptionListMutation> result = new ArrayList<>();

        // Reverse the mutations payloads
        List<ScopedSubscriptionListMutation> reversed = new ArrayList<>(mutations);
        Collections.reverse(reversed);

        Set<String> scopedListIds = new HashSet<>();

        for (ScopedSubscriptionListMutation mutation : reversed) {
            String key = mutation.getScope() + ":" + mutation.getListId();

            if (!scopedListIds.contains(key)) {
                result.add(0, mutation);
                scopedListIds.add(key);
            }
        }

        return result;
    }

    public void apply(Map<String, Set<Scope>> subscriptionLists) {
        Set<Scope> scopes = subscriptionLists.get(listId);

        switch (action) {
            case ACTION_SUBSCRIBE:
                if (scopes == null) {
                    scopes = new HashSet<>();
                    subscriptionLists.put(listId, scopes);
                }
                scopes.add(scope);
                break;
            case ACTION_UNSUBSCRIBE:
                if (scopes != null) {
                    scopes.remove(scope);
                }
                break;
        }

        if (scopes == null || scopes.isEmpty()) {
            subscriptionLists.remove(listId);
        }
    }
}
