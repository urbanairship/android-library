package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

import com.urbanairship.Logger;
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
import java.util.Set;

/**
 * Defines subscription list mutations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class SubscriptionListMutation implements JsonSerializable {

    private static final String KEY_ACTION = "action";
    private static final String KEY_LIST_ID = "list_id";
    private static final String KEY_TIMESTAMP = "timestamp";
    /**
     * Subscribe action.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String ACTION_SUBSCRIBE = "subscribe";
    /**
     * Unsubscribe action.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public static final String ACTION_UNSUBSCRIBE = "unsubscribe";

    @NonNull private final String action;
    @NonNull private final String listId;
    @Nullable private final String timestamp;

    /**
     * Default subscription list mutation constructor.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public SubscriptionListMutation(@NonNull String action, @NonNull String listId, @Nullable String timestamp) {
        this.action = action;
        this.listId = listId;
        this.timestamp = timestamp;
    }

    /**
     * Creates a mutation to subscribe to a list.
     *
     * @param listId The ID of the list to subscribe to.
     * @param timestamp The timestamp in milliseconds.
     * @return A new subscription list mutation.
     */
    @NonNull
    public static SubscriptionListMutation newSubscribeMutation(@NonNull String listId, long timestamp) {
        return new SubscriptionListMutation(ACTION_SUBSCRIBE, listId, DateUtils.createIso8601TimeStamp(timestamp));
    }

    /**
     * Creates a mutation to unsubscribe from a list.
     *
     * @param listId The ID of the list to unsubscribe from.
     * @param timestamp The timestamp in milliseconds.
     * @return A new subscription list mutation.
     */
    @NonNull
    public static SubscriptionListMutation newUnsubscribeMutation(@NonNull String listId, long timestamp) {
        return new SubscriptionListMutation(ACTION_UNSUBSCRIBE, listId, DateUtils.createIso8601TimeStamp(timestamp));
    }

    @NonNull
    public static SubscriptionListMutation fromJsonValue(@NonNull JsonValue input) throws JsonException {
        JsonMap json = input.optMap();

        String action = json.opt(KEY_ACTION).getString();
        String listId = json.opt(KEY_LIST_ID).getString();
        String timestamp = json.opt(KEY_TIMESTAMP).getString();

        if (action == null || listId == null) {
            throw new JsonException("Invalid subscription list mutation: " + json);
        }

        return new SubscriptionListMutation(action, listId, timestamp);
    }

    @NonNull
    public static List<SubscriptionListMutation> fromJsonList(@NonNull JsonList jsonList) {
        List<SubscriptionListMutation> mutations = new ArrayList<>();

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
                      .put(KEY_TIMESTAMP, timestamp)
                      .build()
                      .toJsonValue();
    }

    /**
     * Collapses a collection of mutations into a single mutation payload.
     *
     * @param mutations a list of subscription list mutations to collapse.
     * @return A collapsed {@code SubscriptionListMutation} object.
     */
    public static List<SubscriptionListMutation> collapseMutations(List<SubscriptionListMutation> mutations) {
        List<SubscriptionListMutation> result = new ArrayList<>();

        // Reverse the mutations payloads
        List<SubscriptionListMutation> reversed = new ArrayList<>(mutations);
        Collections.reverse(reversed);

        Set<String> mutationListIds = new HashSet<>();

        for (SubscriptionListMutation mutation : reversed) {
            if (!mutationListIds.contains(mutation.listId)) {
                result.add(0, mutation);
                mutationListIds.add(mutation.listId);
            }
        }

        return result;
    }

    public void apply(Set<String> subscriptions) {
        if (getAction().equals(ACTION_SUBSCRIBE)) {
            subscriptions.add(getListId());
        } else {
            subscriptions.remove(getListId());
        }
    }

    /**
     * Mutation action.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public String getAction() {
        return action;
    }

    /**
     * Mutation list Id.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @NonNull
    public String getListId() {
        return listId;
    }

    /**
     * Mutation timestamp.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Nullable
    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubscriptionListMutation that = (SubscriptionListMutation) o;
        return action.equals(that.action) &&
                listId.equals(that.listId) &&
                ObjectsCompat.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(action, listId, timestamp);
    }

    @Override
    public String toString() {
        return "SubscriptionListMutation{" +
                "action='" + action + '\'' +
                ", listId='" + listId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
