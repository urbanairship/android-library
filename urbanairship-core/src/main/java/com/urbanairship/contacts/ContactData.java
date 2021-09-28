package com.urbanairship.contacts;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private final Map<String, JsonValue> attributes;
    private final Map<String, Set<String>> tagGroups;

    ContactData(@Nullable Map<String, JsonValue> attributes, @Nullable Map<String, Set<String>> tagGroups) {
        this.attributes = attributes == null ? Collections.<String, JsonValue>emptyMap() : Collections.unmodifiableMap(attributes);
        this.tagGroups = tagGroups == null ? Collections.unmodifiableMap(tagGroups) : tagGroups;
    }

    /**
     * Contact attributes.
     * @return The attributes.
     */
    @NonNull
    public Map<String, JsonValue> getAttributes() {
        return attributes;
    }

    /**
     * Contact tag groups.
     * @return The tag groups.
     */
    @NonNull
    public Map<String, Set<String>> getTagGroups() {
        return tagGroups;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                .putOpt(TAG_GROUPS_KEY, tagGroups)
                .putOpt(ATTRIBUTES_KEY, attributes)
                .build().toJsonValue();
    }

    @Nullable
    static ContactData fromJson(@NonNull JsonValue value) {
        JsonMap map = value.optMap();

        Map<String, Set<String>> tagGroups = new HashMap<>();
        for (Map.Entry<String, JsonValue> entry : map.opt(TAG_GROUPS_KEY).optMap()) {
            Set<String> tags = new HashSet<>();

            for (JsonValue tagJson: entry.getValue().optList()) {
                if (tagJson.isString()) {
                    tags.add(tagJson.optString());
                }
            }

            tagGroups.put(entry.getKey(), tags);
        }

        Map<String, JsonValue> attributes = map.opt(ATTRIBUTES_KEY).optMap().getMap();

        if (attributes.isEmpty() && tagGroups.isEmpty()) {
            return null;
        } else {
            return new ContactData(attributes, tagGroups);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactData data = (ContactData) o;
        return ObjectsCompat.equals(attributes, data.attributes) && ObjectsCompat.equals(tagGroups, data.tagGroups);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(attributes, tagGroups);
    }

}
