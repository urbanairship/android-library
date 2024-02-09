/* Copyright Airship and Contributors */

package com.urbanairship.iam.layout;

import com.urbanairship.android.layout.Thomas;
import com.urbanairship.android.layout.info.LayoutInfo;
import com.urbanairship.iam.DisplayContent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonValue;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

/**
 * Display content for a {@link com.urbanairship.iam.InAppMessage#TYPE_AIRSHIP_LAYOUT} in-app message.
 */
public class AirshipLayoutDisplayContent implements DisplayContent {

    private static final String LAYOUT_KEY = "layout";

    private final JsonValue json;
    private final LayoutInfo payload;

    private AirshipLayoutDisplayContent(@NonNull JsonValue json,  @NonNull LayoutInfo basePayload) {
        this.json = json;
        this.payload = basePayload;
    }

    /**
     * Parses HTML display JSON.
     *
     * @param value The json payload.
     * @return The parsed display content.
     * @throws JsonException If the json was unable to be parsed.
     */
    @NonNull
    public static AirshipLayoutDisplayContent fromJson(@NonNull JsonValue value) throws JsonException {
        LayoutInfo basePayload = new LayoutInfo(value.optMap().opt(LAYOUT_KEY).optMap());
        if (!Thomas.isValid(basePayload)) {
            throw new JsonException("Invalid payload.");
        }
        return new AirshipLayoutDisplayContent(value, basePayload);
    }

    /**
     * Whether the payload presentation specifies an embedded layout.
     *
     * @return {@code true} if embedded, otherwise {@code false}.
     */
    public boolean isEmbedded() {
        return payload.isEmbedded();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return this.json;
    }

    /**
     * The parsed payload.
     * @return The base payload.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public LayoutInfo getPayload() {
        return this.payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AirshipLayoutDisplayContent that = (AirshipLayoutDisplayContent) o;
        return ObjectsCompat.equals(json, that.json);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(json);
    }
}
