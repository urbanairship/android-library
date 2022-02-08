/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Open channel registration options.
 */
public class OpenChannelRegistrationOptions implements JsonSerializable {

    public static final String PLATFORM_NAME_KEY = "platform_name";
    public static final String IDENTIFIERS_KEY = "identifiers";

    @NonNull
    private final String platformName;

    @Nullable
    private final Map<String, String> identifiers;


    private OpenChannelRegistrationOptions(@NonNull String platformName, @Nullable Map<String, String> identifiers) {
        this.platformName = platformName;
        this.identifiers = identifiers;
    }

    /**
     * Creates default options.
     *
     * @param platformName The platform name
     * @param identifiers Optional identifiers.
     * @return The open channel options.
     */
    public static OpenChannelRegistrationOptions options(@NonNull String platformName, @Nullable Map<String, String> identifiers) {
        return new OpenChannelRegistrationOptions(platformName, identifiers);
    }

    /**
     * Creates default options.
     *
     * @param platformName The platform name
     * @return The open channel options.
     */
    public static OpenChannelRegistrationOptions options(@NonNull String platformName) {
        return new OpenChannelRegistrationOptions(platformName, null);
    }

    @Nullable
    Map<String, String> getIdentifiers() {
        return identifiers;
    }

    @NonNull
    String getPlatformName() {
        return platformName;
    }

    static OpenChannelRegistrationOptions fromJson(@NonNull JsonValue value) throws JsonException {
        String platformName = value.optMap().opt(PLATFORM_NAME_KEY).requireString();
        JsonMap identifiersJson = value.optMap().opt(IDENTIFIERS_KEY).getMap();

        Map<String, String> parsedIdentifiers = null;
        if (identifiersJson != null) {
            parsedIdentifiers = new HashMap<>();
            for (Map.Entry<String, JsonValue> entry : identifiersJson.entrySet()) {
                parsedIdentifiers.put(entry.getKey(), entry.getValue().requireString());
            }
        }
        return new OpenChannelRegistrationOptions(platformName, parsedIdentifiers);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(PLATFORM_NAME_KEY, platformName)
                      .putOpt(IDENTIFIERS_KEY, identifiers)
                      .build()
                      .toJsonValue();
    }

}
