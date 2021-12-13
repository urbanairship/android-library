/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.reporting;

import com.urbanairship.json.JsonMap;
import com.urbanairship.util.UAStringUtil;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

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

    public boolean isChannel() {
        return !UAStringUtil.isEmpty(channel);
    }

    public boolean isContact() {
        return !UAStringUtil.isEmpty(contact);
    }

    @NonNull
    @Override
    public String toString() {
        return "AttributeName{" +
            "channel='" + channel + '\'' +
            ", contact='" + contact + '\'' +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AttributeName that = (AttributeName) o;
        return ObjectsCompat.equals(channel, that.channel) &&
            ObjectsCompat.equals(contact, that.contact);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(channel, contact);
    }
}
