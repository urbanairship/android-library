/* Copyright Airship and Contributors */

package com.urbanairship.contacts;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;

/**
 * Sms registration options.
 */
public class SmsRegistrationOptions implements JsonSerializable {

    public static final String SENDER_ID_KEY = "sender_id";

    @NonNull
    private final String senderId;

    private SmsRegistrationOptions(@NonNull String senderId) {
        this.senderId = senderId;
    }

    /**
     * Creates default options.
     * @param senderId The sender Id.
     * @return The sms options.
     */
    @NonNull
    public static SmsRegistrationOptions options(@NonNull String senderId) {
        return new SmsRegistrationOptions(senderId);
    }

    @NonNull
    String getSenderId() {
        return senderId;
    }

    @NonNull
    static SmsRegistrationOptions fromJson(@NonNull JsonValue value) throws JsonException {
        String senderId = value.optMap().opt(SENDER_ID_KEY).requireString();
        return new SmsRegistrationOptions(senderId);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(SENDER_ID_KEY, senderId)
                      .build()
                      .toJsonValue();
    }

}
