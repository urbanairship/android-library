/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;

/**
 * A contact associated channel.
 */
public class AssociatedChannel implements JsonSerializable {

    private final static String CHANNEL_ID_KEY = "channel_id";
    private final static String CHANNEL_TYPE_KEY = "channel_type";

    private String channelId;
    private ChannelType channelType;

    AssociatedChannel(@NonNull String channelId, @NonNull ChannelType channelType) {
        this.channelId = channelId;
        this.channelType = channelType;
    }

    /**
     * The channel type.
     *
     * @return The channel type.
     */
    @NonNull
    public ChannelType getChannelType() {
        return channelType;
    }

    /**
     * The channel ID.
     *
     * @return The channel ID.
     */
    public String getChannelId() {
        return channelId;
    }

    @Override
    @NonNull
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(CHANNEL_TYPE_KEY, channelType.toString())
                      .put(CHANNEL_ID_KEY, channelId)
                      .build()
                      .toJsonValue();
    }

    @NonNull
    static AssociatedChannel fromJson(@NonNull JsonValue value) throws JsonException {
        String channel = value.optMap().opt(CHANNEL_ID_KEY).requireString();
        String typeString = value.optMap().opt(CHANNEL_TYPE_KEY).requireString();
        ChannelType channelType;

        try {
            channelType = ChannelType.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid channel type " + typeString, e);
        }

        return new AssociatedChannel(channel, channelType);
    }

}
