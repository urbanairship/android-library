/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.channel.AttributeMutation;
import com.urbanairship.channel.TagGroupsMutation;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class ContactOperation implements JsonSerializable {

    static final String OPERATION_UPDATE = "UPDATE";
    static final String OPERATION_IDENTIFY = "IDENTIFY";
    static final String OPERATION_RESOLVE = "RESOLVE";
    static final String OPERATION_RESET = "RESET";
    static final String OPERATION_REGISTER_EMAIL = "REGISTER_EMAIL";
    static final String OPERATION_REGISTER_SMS = "REGISTER_SMS";
    static final String OPERATION_REGISTER_OPEN_CHANNEL = "REGISTER_OPEN_CHANNEL";
    static final String OPERATION_ASSOCIATE_CHANNEL = "ASSOCIATE_CHANNEL";

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
    static ContactOperation associateChannel(@NonNull String channelId, @NonNull ChannelType channelType) {
        return new ContactOperation(OPERATION_ASSOCIATE_CHANNEL, new AssociateChannelPayload(channelId, channelType));
    }

    @NonNull
    static ContactOperation registerEmail(@NonNull String address, @NonNull EmailRegistrationOptions options) {
        return new ContactOperation(OPERATION_REGISTER_EMAIL, new RegisterEmailPayload(address, options));
    }

    @NonNull
    static ContactOperation registerSms(@NonNull String msisdn, @NonNull SmsRegistrationOptions options) {
        return new ContactOperation(OPERATION_REGISTER_SMS, new RegisterSmsPayload(msisdn, options));
    }

    @NonNull
    static ContactOperation registerOpenChannel(@NonNull String address, @NonNull OpenChannelRegistrationOptions options) {
        return new ContactOperation(OPERATION_REGISTER_OPEN_CHANNEL, new RegisterOpenChannelPayload(address, options));
    }

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
            throw new JsonException("Invalid contact operation  " + value);
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
            case OPERATION_REGISTER_EMAIL:
                payload = RegisterEmailPayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            case OPERATION_REGISTER_SMS:
                payload = RegisterSmsPayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            case OPERATION_REGISTER_OPEN_CHANNEL:
                payload = RegisterOpenChannelPayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            case OPERATION_ASSOCIATE_CHANNEL:
                payload = AssociateChannelPayload.fromJson(map.opt(PAYLOAD_KEY));
                break;
            default:
                throw new JsonException("Invalid contact operation  " + value);
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
            String identifier = jsonValue.requireString();
            return new IdentifyPayload(identifier);
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
            this.tagGroupMutations = tagGroupMutations == null ? Collections.emptyList() : tagGroupMutations;
            this.attributeMutations = attributeMutations == null ? Collections.emptyList() : attributeMutations;
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

        @NonNull
        public List<ScopedSubscriptionListMutation> getSubscriptionListMutations() {
            return subscriptionListMutations;
        }

        @NonNull
        public static UpdatePayload fromJson(@NonNull JsonValue value) {
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
                    ", attributeMutations= " + attributeMutations +
                    ", subscriptionListMutations=" + subscriptionListMutations +
                    '}';
        }

    }

    static class RegisterEmailPayload implements Payload {

        private static final String EMAIL_ADDRESS_KEY = "EMAIL_ADDRESS";
        private static final String OPTIONS_KEY = "OPTIONS";

        private final String emailAddress;
        private final EmailRegistrationOptions options;

        public RegisterEmailPayload(@NonNull String emailAddress, @NonNull EmailRegistrationOptions options) {
            this.emailAddress = emailAddress;
            this.options = options;
        }

        @NonNull
        public String getEmailAddress() {
            return emailAddress;
        }

        @NonNull
        public EmailRegistrationOptions getOptions() {
            return options;
        }

        @NonNull
        public static RegisterEmailPayload fromJson(@NonNull JsonValue value) throws JsonException {
            String address = value.optMap().opt(EMAIL_ADDRESS_KEY).requireString();
            EmailRegistrationOptions options = EmailRegistrationOptions.fromJson(value.optMap().opt(OPTIONS_KEY));
            return new RegisterEmailPayload(address, options);
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(EMAIL_ADDRESS_KEY, emailAddress)
                          .put(OPTIONS_KEY, options)
                          .build()
                          .toJsonValue();
        }

    }

    static class RegisterSmsPayload implements Payload {

        private static final String MSISDN_KEY = "MSISDN";
        private static final String OPTIONS_KEY = "OPTIONS";

        private final String msisdn;
        private final SmsRegistrationOptions options;

        public RegisterSmsPayload(@NonNull String msisdn, @NonNull SmsRegistrationOptions options) {
            this.msisdn = msisdn;
            this.options = options;
        }

        public String getMsisdn() {
            return msisdn;
        }

        public SmsRegistrationOptions getOptions() {
            return options;
        }

        @NonNull
        public static RegisterSmsPayload fromJson(@NonNull JsonValue value) throws JsonException {
            String address = value.optMap().opt(MSISDN_KEY).requireString();
            SmsRegistrationOptions options = SmsRegistrationOptions.fromJson(value.optMap().opt(OPTIONS_KEY));
            return new RegisterSmsPayload(address, options);
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(MSISDN_KEY, msisdn)
                          .put(OPTIONS_KEY, options)
                          .build()
                          .toJsonValue();
        }

    }

    static class RegisterOpenChannelPayload implements Payload {

        private static final String ADDRESS_KEY = "ADDRESS";
        private static final String OPTIONS_KEY = "OPTIONS";

        private final String address;
        private final OpenChannelRegistrationOptions options;

        public RegisterOpenChannelPayload(@NonNull String address, @NonNull OpenChannelRegistrationOptions options) {
            this.address = address;
            this.options = options;
        }

        @NonNull
        public String getAddress() {
            return address;
        }

        @NonNull
        public OpenChannelRegistrationOptions getOptions() {
            return options;
        }

        @NonNull
        public static RegisterOpenChannelPayload fromJson(@NonNull JsonValue value) throws JsonException {
            String address = value.optMap().opt(ADDRESS_KEY).requireString();
            OpenChannelRegistrationOptions options = OpenChannelRegistrationOptions.fromJson(value.optMap().opt(OPTIONS_KEY));
            return new RegisterOpenChannelPayload(address, options);
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(ADDRESS_KEY, address)
                          .put(OPTIONS_KEY, options)
                          .build()
                          .toJsonValue();
        }

    }

    static class AssociateChannelPayload implements Payload {

        private static final String CHANNEL_ID_KEY = "CHANNEL_ID";
        private static final String CHANNEL_TYPE_KEY = "CHANNEL_TYPE";

        private final String channelId;
        private final ChannelType channelType;

        public AssociateChannelPayload(@NonNull String channelId, @NonNull ChannelType channelType) {
            this.channelId = channelId;
            this.channelType = channelType;
        }

        @NonNull
        public String getChannelId() {
            return channelId;
        }

        @NonNull
        public ChannelType getChannelType() {
            return channelType;
        }

        @NonNull
        public static AssociateChannelPayload fromJson(@NonNull JsonValue value) throws JsonException {
            String channel = value.optMap().opt(CHANNEL_ID_KEY).requireString();
            String typeString = value.optMap().opt(CHANNEL_TYPE_KEY).requireString();

            try {
                ChannelType channelType = ChannelType.valueOf(typeString);
                return new AssociateChannelPayload(channel, channelType);
            } catch (IllegalArgumentException e) {
                throw new JsonException("Invalid channel type " + typeString, e);
            }
        }

        @NonNull
        @Override
        public JsonValue toJsonValue() {
            return JsonMap.newBuilder()
                          .put(CHANNEL_ID_KEY, channelId)
                          .put(CHANNEL_TYPE_KEY, channelType.name())
                          .build()
                          .toJsonValue();
        }

    }

}
