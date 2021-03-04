/* Copyright Airship and Contributors */

package com.urbanairship.remotedata;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Model representing a remote data payload.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataPayload {

    /**
     * Key for the language used to fetch the remote data payload.
     */
    @NonNull
    public static final String METADATA_LANGUAGE = "language";

    /**
     * Key for the country used to fetch the remote data payload.
     */
    @NonNull
    public static final String METADATA_COUNTRY = "country";

    /**
     * Key for the SDK version used to fetch the remote data payload.
     */
    @NonNull
    public static final String METADATA_SDK_VERSION = "sdk_version";

    @NonNull
    private final String type;
    private final long timestamp;
    @NonNull
    private final JsonMap data;
    @NonNull
    private final JsonMap metadata;

    private RemoteDataPayload(@NonNull Builder builder) {
        this.type = builder.type;
        this.timestamp = builder.timestamp;
        this.data = builder.data;
        this.metadata = builder.metadata == null ? JsonMap.EMPTY_MAP : builder.metadata;
    }

    /**
     * Creates an empty payload.
     *
     * @param type The type.
     * @return The empty payload.
     */
    @NonNull
    static RemoteDataPayload emptyPayload(@NonNull String type) {
        return RemoteDataPayload.newBuilder()
                                .setType(type)
                                .setTimeStamp(0)
                                .setData(JsonMap.EMPTY_MAP)
                                .build();
    }

    /**
     * Parses a remote data payload from JSON
     *
     * @param value The Json
     * @param metadata The metadata used to fetch the payload.
     * @return A RemoteDataPayload
     * @throws JsonException if the remote data payload's json value is invalid.
     */
    @NonNull
    static RemoteDataPayload parsePayload(@NonNull JsonValue value, @NonNull JsonMap metadata) throws JsonException {
        JsonMap map = value.optMap();
        JsonValue type = map.opt("type");
        JsonValue isoTimestamp = map.opt("timestamp");
        JsonValue data = map.opt("data");
        try {
            if (type.isString() && isoTimestamp.isString() && data.isJsonMap()) {
                long timestampMs = DateUtils.parseIso8601(isoTimestamp.getString());
                return RemoteDataPayload.newBuilder()
                                        .setData(data.optMap())
                                        .setTimeStamp(timestampMs)
                                        .setType(type.optString())
                                        .setMetadata(metadata)
                                        .build();
            } else {
                throw new JsonException("Invalid remote data payload: " + value.toString());
            }
        } catch (IllegalArgumentException | ParseException e) {
            throw new JsonException("Invalid remote data payload: " + value.toString(), e);
        }
    }

    /**
     * Parses remote data payloads from JSON.
     *
     * @param list The JSON list of payloads.
     * @param metadata The metadata used to fetch the payloads.
     * @return A List of RemoteDataPayloads.
     */
    @NonNull
    static Set<RemoteDataPayload> parsePayloads(@NonNull JsonList list, @NonNull JsonMap metadata) {
        try {
            Set<RemoteDataPayload> payloads = new HashSet<>();

            for (JsonValue payload : list) {
                payloads.add(parsePayload(payload, metadata));
            }
            return payloads;
        } catch (JsonException e) {
            Logger.error("Unable to parse remote data payloads: %s", list);
        }

        return Collections.emptySet();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoteDataPayload payload = (RemoteDataPayload) o;

        if (timestamp != payload.timestamp) return false;
        if (!type.equals(payload.type)) return false;
        if (!data.equals(payload.data)) return false;
        return metadata.equals(payload.metadata);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + data.hashCode();
        result = 31 * result + metadata.hashCode();
        return result;
    }

    @Override
    @NonNull
    public String toString() {
        return "RemoteDataPayload{" +
                "type='" + type + '\'' +
                ", timestamp=" + timestamp +
                ", data=" + data +
                ", metadata=" + metadata +
                '}';
    }

    /**
     * Gets the type.
     *
     * @return The type.
     */
    @NonNull
    public final String getType() {
        return type;
    }

    /**
     * Gets the timestamp.
     *
     * @return The timestamp.
     */
    public final long getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the data.
     *
     * @return The data.
     */
    @NonNull
    public final JsonMap getData() {
        return data;
    }

    /**
     * Gets the metadata used to fetch the payload.
     *
     * @return The metadata map.
     */
    @NonNull
    public final JsonMap getMetadata() {
        return metadata;
    }

    /**
     * Creates a new builder.
     *
     * @return A new builder.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String type;
        private long timestamp;
        private JsonMap data;
        private JsonMap metadata;

        /**
         * Sets the payload's type.
         *
         * @param type The type.
         * @return The builder.
         */
        @NonNull
        public Builder setType(@Nullable String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the payload's timestamp.
         *
         * @param timestamp The type.
         * @return The builder.
         */
        @NonNull
        public Builder setTimeStamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Sets the payload's data.
         *
         * @param data The data.
         * @return The builder.
         */
        @NonNull
        public Builder setData(@Nullable JsonMap data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the payload's metadata.
         *
         * @param metadata The metadata.
         * @return The builder.
         */
        @NonNull
        public Builder setMetadata(@Nullable JsonMap metadata) {
            this.metadata = metadata;
            return this;
        }

        /**
         * Builds the payload.
         *
         * @return The payload instance.
         * @throws IllegalArgumentException If the type or data is null.
         */
        @NonNull
        public RemoteDataPayload build() {
            Checks.checkNotNull(type, "Missing type");
            Checks.checkNotNull(data, "Missing data");
            return new RemoteDataPayload(this);
        }

    }

}
