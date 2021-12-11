/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AttributeName {
    @Nullable
    private final String channel;
    @Nullable
    private final String contact;

    public AttributeName(@Nullable String channel, @Nullable String contact) {
        this.channel = channel;
        this.contact = contact;
    }

    @Nullable
    public static AttributeName fromJson(@NonNull JsonMap json) {
        String channel = json.opt("channel").getString();
        String contact = json.opt("contact").getString();
        if (channel != null || contact != null) {
            return new AttributeName(channel, contact);
        } else {
            return null;
        }
    }

    @Nullable
    public static AttributeName attributeNameFromJson(@NonNull JsonMap json) {
        JsonMap attributeNameJson = json.opt("attribute_name").optMap();
        return fromJson(attributeNameJson);
    }

    @Nullable
    public String getChannel() {
        return channel;
    }

    @Nullable
    public String getContact() {
        return contact;
    }
}
