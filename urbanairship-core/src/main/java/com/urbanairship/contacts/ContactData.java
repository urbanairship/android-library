/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

/**
 * Contact data.
 */
public class ContactData implements JsonSerializable {

    private static final String TAG_GROUPS_KEY = "tag_groups";
    private static final String ATTRIBUTES_KEY = "attributes";
    private static final String ASSOCIATED_CHANNELS_KEY = "associated_channels";
    private static final String SUBSCRIPTION_LISTS = "subscription_lists";

    private final Map<String, JsonValue> attributes;
    private final Map<String, Set<String>> tagGroups;
    private final List<AssociatedChannel> associatedChannels;
    private final Map<String, Set<Scope>> subscriptionLists;

    ContactData(@Nullable Map<String, JsonValue> attributes,
                @Nullable Map<String, Set<String>> tagGroups,
                @Nullable List<AssociatedChannel> associatedChannels,
                @Nullable Map<String, Set<Scope>> subscriptionLists) {
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
        this.tagGroups = tagGroups == null ? Collections.emptyMap() : Collections.unmodifiableMap(tagGroups);
        this.associatedChannels = associatedChannels == null ? Collections.emptyList() : Collections.unmodifiableList(associatedChannels);
        this.subscriptionLists = subscriptionLists == null ? Collections.emptyMap() : Collections.unmodifiableMap(subscriptionLists);
    }

    /**
     * Contact attributes.
     *
     * @return The attributes.
     */
    @NonNull
    public Map<String, JsonValue> getAttributes() {
        return attributes;
    }

    /**
     * Contact tag groups.
     *
     * @return The tag groups.
     */
    @NonNull
    public Map<String, Set<String>> getTagGroups() {
        return tagGroups;
    }

    /**
     * A list of associated contacts.
     *
     * @return The list of associated contacts.
     */
    @NonNull
    public List<AssociatedChannel> getAssociatedChannels() {
        return associatedChannels;
    }

    /**
     * Contact subscription lists.
     *
     * @return The subscription lists.
     */
    @NonNull
    public Map<String, Set<Scope>> getSubscriptionLists() {
        return subscriptionLists;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(TAG_GROUPS_KEY, tagGroups)
                      .putOpt(ATTRIBUTES_KEY, attributes)
                      .putOpt(ASSOCIATED_CHANNELS_KEY, associatedChannels)
                      .putOpt(SUBSCRIPTION_LISTS, subscriptionLists)
                      .build().toJsonValue();
    }

    @Nullable
    static ContactData fromJson(@NonNull JsonValue value) throws JsonException {
        JsonMap map = value.optMap();

        Map<String, Set<String>> tagGroups = new HashMap<>();
        for (Map.Entry<String, JsonValue> entry : map.opt(TAG_GROUPS_KEY).optMap()) {
            Set<String> tags = new HashSet<>();

            for (JsonValue tagJson : entry.getValue().optList()) {
                if (tagJson.isString()) {
                    tags.add(tagJson.optString());
                }
            }

            tagGroups.put(entry.getKey(), tags);
        }

        Map<String, Set<Scope>> subscriptionLists = new HashMap<>();
        for (Map.Entry<String, JsonValue> entry : map.opt(SUBSCRIPTION_LISTS).optMap()) {
            Set<Scope> scopes = new HashSet<>();

            for (JsonValue scopeJson : entry.getValue().optList()) {
                scopes.add(Scope.fromJson(scopeJson));
            }

            subscriptionLists.put(entry.getKey(), scopes);
        }

        Map<String, JsonValue> attributes = map.opt(ATTRIBUTES_KEY).optMap().getMap();

        List<AssociatedChannel> channels = new ArrayList<>();
        for (JsonValue jsonValue : map.opt(ASSOCIATED_CHANNELS_KEY).optList().getList()) {
            channels.add(AssociatedChannel.fromJson(jsonValue));
        }

        if (attributes.isEmpty() && tagGroups.isEmpty() && channels.isEmpty() && subscriptionLists.isEmpty()) {
            return null;
        } else {
            return new ContactData(attributes, tagGroups, channels, subscriptionLists);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactData data = (ContactData) o;
        return ObjectsCompat.equals(attributes, data.attributes) &&
                ObjectsCompat.equals(tagGroups, data.tagGroups) &&
                ObjectsCompat.equals(associatedChannels, data.associatedChannels) &&
                ObjectsCompat.equals(subscriptionLists, data.subscriptionLists);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(attributes, tagGroups, associatedChannels, subscriptionLists);
    }

}
