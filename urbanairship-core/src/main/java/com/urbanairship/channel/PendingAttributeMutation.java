package com.urbanairship.channel;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class PendingAttributeMutation extends AttributeMutation implements JsonSerializable {
    private final String timestamp;

    private PendingAttributeMutation(@NonNull String action, @NonNull String key, @Nullable Object value, @NonNull String timestamp) {
        super(action, key, value);
        this.timestamp = timestamp;
    }

    @Nullable
    static List<PendingAttributeMutation> fromAttributeMutations(@NonNull List<AttributeMutation> mutations, @NonNull long timestamp) {
        List<PendingAttributeMutation> list = new ArrayList<>();

        String timestampString = DateUtils.createIso8601TimeStamp(timestamp);

        for (AttributeMutation mutation : mutations) {
            String action = mutation.getMutationAction();
            String name = mutation.getMutationName();
            Object value = mutation.getMutationValue();

            PendingAttributeMutation pendingMutation = new PendingAttributeMutation(action, name, value, timestampString);
            list.add(pendingMutation);
        }

        return list;
    }

    @Nullable
    static PendingAttributeMutation fromJsonValue(@NonNull JsonValue jsonValue) {
        JsonMap mutation = jsonValue.optMap();

        String action = mutation.opt(ATTRIBUTE_ACTION_KEY).getString();
        String name = mutation.opt(ATTRIBUTE_NAME_KEY).getString();
        Object value = mutation.opt(ATTRIBUTE_VALUE_KEY).getValue();
        String timestamp = mutation.opt(ATTRIBUTE_TIMESTAMP_KEY).getString();

        if (action == null) {
            return null;
        }

        if (name == null) {
            return null;
        }

        return new PendingAttributeMutation(action, name, value, timestamp);
    }

    @NonNull
    static List<PendingAttributeMutation> fromJsonList(@NonNull JsonList jsonList) {
        List<PendingAttributeMutation> mutations = new ArrayList<>();

        for (JsonValue value : jsonList) {
            PendingAttributeMutation mutation = fromJsonValue(value);

            if (mutation != null) {
                mutations.add(mutation);
            }
        }

        return mutations;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        JsonMap.Builder builder = JsonMap.newBuilder();

        builder.put(ATTRIBUTE_ACTION_KEY, JsonValue.wrapOpt(getMutationAction()));
        builder.put(ATTRIBUTE_NAME_KEY, JsonValue.wrapOpt(getMutationName()));
        builder.putOpt(ATTRIBUTE_VALUE_KEY, JsonValue.wrapOpt(getMutationValue()));
        builder.putOpt(ATTRIBUTE_TIMESTAMP_KEY, JsonValue.wrapOpt(getMutationTimestamp()));

        return builder.build().toJsonValue();
    }

    /**
     * Collapses a collection of mutation payloads to a single mutation payload.
     *
     * @param mutations a list of attribute mutation instances to collapse.
     * @return An attribute mutations instance.
     */
    @NonNull
    static List<PendingAttributeMutation> collapseMutations(@NonNull List<PendingAttributeMutation> mutations) {
        List<PendingAttributeMutation> result = new ArrayList<>();

        // Reverse the mutations payloads
        List<PendingAttributeMutation> reversed = new ArrayList<>(mutations);
        Collections.reverse(reversed);

        Set<String> mutationNames = new HashSet<>();

        for (PendingAttributeMutation mutation : reversed) {
            if (!mutationNames.contains(mutation.getMutationName())) {
                result.add(0, mutation);
                mutationNames.add(mutation.getMutationName());
            }
        }

        return result;
    }

    /**
     * Gets an attribute mutation's timestamp.
     *
     * @return An attribute mutation's timestamp.
     */
    @Nullable
    private String getMutationTimestamp() {
        return timestamp;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }
}
