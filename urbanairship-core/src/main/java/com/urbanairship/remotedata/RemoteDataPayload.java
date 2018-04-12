/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.remotedata;

import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.DateUtils;

import java.text.ParseException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Model representing a remote data payload.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RemoteDataPayload {

    private final String type;
    private final long timestamp;
    private final JsonMap data;

    /**
     * RemoteDataPayload constructor.
     *
     * @param type The type.
     * @param timestamp The timestamp.
     * @param data The data.
     */
    public RemoteDataPayload(String type, long timestamp, JsonMap data) {
        this.type = type;
        this.timestamp = timestamp;
        this.data = data;
    }

    /**
     * RemoteDataPayload constructor.
     * @param entry The associated payload entry

     * @throws JsonException
     */
    RemoteDataPayload(RemoteDataPayloadEntry entry) throws JsonException {
        this.type = entry.type;
        this.timestamp = entry.timestamp;
        this.data = JsonValue.parseString(entry.data).getMap();
    }

    /**
     * Parses a remote data payload from JSON
     *
     * @param value The Json
     * @return A RemoteDataPayload
     * @throws JsonException
     */
    public static RemoteDataPayload parsePayload(@NonNull JsonValue value) throws JsonException {
        JsonMap map = value.optMap();
        JsonValue type = map.opt("type");
        JsonValue isoTimestamp = map.opt("timestamp");
        JsonValue data = map.opt("data");
        try {
            if (type.isString() && isoTimestamp.isString() && data.isJsonMap()) {
                long timestampMs = DateUtils.parseIso8601(isoTimestamp.getString());
                return new RemoteDataPayload(type.getString(), timestampMs, data.getMap());
            } else {
                throw new JsonException("Invalid remote data payload: " + value.toString());
            }
        } catch (ParseException e) {
            Logger.error("Unable to parse timestamp: " + isoTimestamp);
            throw new JsonException("Invalid remote data payload: " + value.toString(), e);
        }
    }

    /**
     * Parses remote data payloads from JSON.
     *
     * @param value The JSON.
     * @return A List of RemoteDataPayloads.
     */
    public static Set<RemoteDataPayload> parsePayloads(@NonNull JsonValue value) {
        JsonList list = value.optList();

        try {
            Set<RemoteDataPayload> payloads = new HashSet<>();

            for (JsonValue payload : list) {
                payloads.add(parsePayload(payload));
            }
            return payloads;
        } catch (JsonException e) {
            Logger.error("Unable to parse remote data payloads: " + value.toString());
        }

        return Collections.emptySet();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        RemoteDataPayload payload = (RemoteDataPayload) o;

        if (timestamp != payload.timestamp) {
            return false;
        }
        if (!type.equals(payload.type)) {
            return false;
        }
        return data.equals(payload.data);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        result = 31 * result + data.hashCode();
        return result;
    }


    @Override
    public String toString() {
        return "RemoteDataPayload{" +
                "type='" + type + '\'' +
                ", timestamp=" + timestamp +
                ", data=" + data +
                '}';
    }

    /**
     * Gets the type.
     *
     * @return The type.
     */
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
    public final JsonMap getData() {
        return data;
    }
}
