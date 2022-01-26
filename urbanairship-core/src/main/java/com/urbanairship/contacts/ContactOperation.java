/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.SubscriptionListMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.List;

class ContactOperation implements JsonSerializable {
    static final String OPERATION_UPDATE = "UPDATE";
    static final String OPERATION_IDENTIFY = "IDENTIFY";
    static final String OPERATION_RESOLVE = "RESOLVE";
    static final String OPERATION_RESET = "RESET";

    static final String TYPE_KEY = "TYPE_KEY";
    static final String PAYLOAD_KEY = "PAYLOAD_KEY";

    private final String type;
    private final Payload payload;

    private ContactOperation(@NonNull String type, @Nullable Payload payload) {
        this.type = type;
        this.payload = payload;
    }

    @NonNull
    static ContactOperation identify(@NonNull String identifier) {
        return new ContactOperation(OPERATION_IDENTIFY, new IdentifyPayload(identifier));
    }

    @NonNull
    static ContactOperation reset() {
        return new ContactOperation(OPERATION_RESET, null);
    }

    @NonNull
    static ContactOperation resolve() {
        return new ContactOperation(OPERATION_RESOLVE, null);
    }

    @NonNull
    static ContactOperation update(@Nullable List<TagGroupsMutation> tagGroupMutations,
                                   @Nullable List<AttributeMutation> attributeMutations,
                                   @Nullable List<ScopedSubscriptionListMutation> subscriptionMutations) {
        return new ContactOperation(OPERATION_UPDATE, new UpdatePayload(tagGroupMutations, attributeMutations, subscriptionMutations));
    }

    @NonNull
    static ContactOperation updateTags(@Nullable List<TagGroupsMutation> tagGroupMutations) {
        return update(tagGroupMutations, null, null);
    }

    @NonNull
    static ContactOperation updateAttributes(@Nullable List<AttributeMutation> attributeMutations) {
        return update(null, attributeMutations, null);
    }

    @NonNull
    static ContactOperation updateSubscriptionLists(@Nullable List<ScopedSubscriptionListMutation> subscriptionListMutations) {
        return update(null, null, subscriptionListMutations);
    }


    @NonNull
    static ContactOperation fromJson(JsonValue value) throws JsonException {
        JsonMap map = value.optMap();
        String type = map.opt(TYPE_KEY).getString();
        if (type == null) {
            throw  new JsonException("Invalid contact operation  " + value);
        }

        Payload payload = null;
        switch (type) {
            case OPERATION_IDENTIFY:
                payload = IdentifyPayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            case OPERATION_UPDATE:
                payload = UpdatePayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            case OPERATION_RESET:
            case OPERATION_RESOLVE:
                break;
        }

        return new ContactOperation(type, payload);
    }

    @NonNull
    public String getType() {
        return type;
    }

    @Nullable
    public Payload getPayload() {
        return payload;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    public <S extends Payload> S coercePayload() {
        if (payload == null) {
            throw new IllegalArgumentException("Payload is null!");
        }
        try {
            return (S) payload;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("Unexpected data", e);
        }
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .put(TYPE_KEY, type)
                .putOpt(PAYLOAD_KEY, payload)
                .build().toJsonValue();
    }

    @Override
    public String toString() {
        return "ContactOperation{" +
                "type='" + type + '\'' +
                ", payload=" + payload +
                '}';
    }

    interface Payload extends JsonSerializable {

    }

    /**
     * Contact operation payload to identify the contact.
     */
    static class IdentifyPayload implements Payload {
        private final String identifier;

        public IdentifyPayload(@NonNull String identifier) {
            this.identifier = identifier;
        }

        @NonNull
        public String getIdentifier() {
            return identifier;
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonValue.wrapOpt(identifier);
        }

        @NonNull
        public static IdentifyPayload fromJson(@NonNull JsonValue jsonValue) throws JsonException {
            String identifier = jsonValue.getString();
            if (identifier != null) {
                return new IdentifyPayload(identifier);
            } else {
                throw new JsonException("Invalid payload: " + jsonValue);
            }
        }

        @Override
        public String toString() {
            return "IdentifyPayload{" +
                    "identifier='" + identifier + '\'' +
                    '}';
        }
    }

    /**
     * Contact operation payload to update tags & attributes.
     */
    static class UpdatePayload implements Payload {
        private static final String TAG_GROUP_MUTATIONS_KEY = "TAG_GROUP_MUTATIONS_KEY";
        private static final String ATTRIBUTE_MUTATIONS_KEY = "ATTRIBUTE_MUTATIONS_KEY";
        private static final String SUBSCRIPTION_LISTS_MUTATIONS_KEY = "SUBSCRIPTION_LISTS_MUTATIONS_KEY";

        private final List<TagGroupsMutation> tagGroupMutations;
        private final List<AttributeMutation> attributeMutations;
        private final List<ScopedSubscriptionListMutation> subscriptionListMutations;

        public UpdatePayload(@Nullable List<TagGroupsMutation> tagGroupMutations,
                             @Nullable List<AttributeMutation> attributeMutations,
                             @Nullable List<ScopedSubscriptionListMutation> subscriptionListMutations) {
            this.tagGroupMutations = tagGroupMutations == null ? Collections.<TagGroupsMutation>emptyList() : tagGroupMutations;
            this.attributeMutations = attributeMutations == null ? Collections.<AttributeMutation>emptyList() : attributeMutations;
            this.subscriptionListMutations = subscriptionListMutations == null ? Collections.emptyList() : subscriptionListMutations;
        }

        @NonNull
        public List<TagGroupsMutation> getTagGroupMutations() {
            return tagGroupMutations;
        }

        @NonNull
        public List<AttributeMutation> getAttributeMutations() {
            return attributeMutations;
        }


        public List<ScopedSubscriptionListMutation> getSubscriptionListMutations() {
            return subscriptionListMutations;
        }

        @Nullable
        public static UpdatePayload fromJson(@NonNull JsonValue value) {
            if (value.isNull()) {
                return null;
            }
            JsonMap map = value.optMap();
            List<TagGroupsMutation> tagGroupMutations = TagGroupsMutation.fromJsonList(map.opt(TAG_GROUP_MUTATIONS_KEY).optList());
            List<AttributeMutation> attributeMutations = AttributeMutation.fromJsonList(map.opt(ATTRIBUTE_MUTATIONS_KEY).optList());
            List<ScopedSubscriptionListMutation> subscriptionListMutations = ScopedSubscriptionListMutation.fromJsonList(map.opt(SUBSCRIPTION_LISTS_MUTATIONS_KEY).optList());
            return new UpdatePayload(tagGroupMutations, attributeMutations, subscriptionListMutations);
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(TAG_GROUP_MUTATIONS_KEY, JsonValue.wrapOpt(tagGroupMutations))
                          .put(ATTRIBUTE_MUTATIONS_KEY, JsonValue.wrapOpt(attributeMutations))
                          .put(SUBSCRIPTION_LISTS_MUTATIONS_KEY, JsonValue.wrapOpt(subscriptionListMutations))
                          .build().toJsonValue();
        }

        @Override
        public String toString() {
            return "UpdatePayload{" +
                    "tagGroupMutations=" + tagGroupMutations +
                    ", attributeMutations=" + attributeMutations +
                    ", subscriptionListMutations=" + subscriptionListMutations +
                    '}';
        }
    }

}
