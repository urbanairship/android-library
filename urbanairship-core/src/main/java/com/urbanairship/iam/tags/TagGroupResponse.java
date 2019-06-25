/* Copyright Airship and Contributors */

package com.urbanairship.iam.tags;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.urbanairship.http.Response;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Map;
import java.util.Set;

/**
 * Contains the parsed tag group lookup response.
 */
class TagGroupResponse implements JsonSerializable {

    /**
     * The tags.
     */
    final Map<String, Set<String>> tags;

    /**
     * The last modified time.
     */
    final String lastModifiedTime;

    final int status;

    private static final String TAG_GROUPS_KEY = "tag_groups";
    private static final String LAST_MODIFIED_KEY = "last_modified";
    private static final String STATUS_KEY = "status";

    @VisibleForTesting
    TagGroupResponse(int status, Map<String, Set<String>> tags, String lastModifiedTime) {
        this.tags = tags;
        this.lastModifiedTime = lastModifiedTime;
        this.status = status;
    }

    /**
     * Factory method to create the response from a JsonValue.
     *
     * @param jsonValue The json value.
     * @return A tag group response.
     */
    static TagGroupResponse fromJsonValue(@NonNull JsonValue jsonValue) {
        JsonMap body = jsonValue.optMap();

        int status = body.opt(STATUS_KEY).getInt(0);
        String lastModified = body.opt(LAST_MODIFIED_KEY).getString();
        Map<String, Set<String>> tags = TagGroupUtils.parseTags(body.opt(TAG_GROUPS_KEY));

        return new TagGroupResponse(status, tags, lastModified);
    }

    /**
     * Factory method to create the response from a request response.
     *
     * @param response The request response.
     * @return A tag group response.
     */
    static TagGroupResponse fromResponse(@NonNull Response response) throws JsonException {
        if (response.getStatus() != 200) {
            return new TagGroupResponse(response.getStatus(), null, null);
        }

        JsonMap body = JsonValue.parseString(response.getResponseBody()).optMap();

        int status = response.getStatus();
        String lastModified = body.opt(LAST_MODIFIED_KEY).getString();
        Map<String, Set<String>> tags = TagGroupUtils.parseTags(body.opt(TAG_GROUPS_KEY));

        return new TagGroupResponse(status, tags, lastModified);
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(TAG_GROUPS_KEY, tags)
                      .put(LAST_MODIFIED_KEY, lastModifiedTime)
                      .put(STATUS_KEY, status)
                      .build()
                      .toJsonValue();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TagGroupResponse response = (TagGroupResponse) o;

        if (status != response.status) {
            return false;
        }

        if (tags != null ? !tags.equals(response.tags) : response.tags != null) {
            return false;
        }

        return lastModifiedTime != null ? lastModifiedTime.equals(response.lastModifiedTime) : response.lastModifiedTime == null;
    }

    @Override
    public int hashCode() {
        int result = tags != null ? tags.hashCode() : 0;
        result = 31 * result + (lastModifiedTime != null ? lastModifiedTime.hashCode() : 0);
        result = 31 * result + status;
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "TagGroupResponse{" +
                "tags=" + tags +
                ", lastModifiedTime='" + lastModifiedTime + '\'' +
                ", status=" + status +
                '}';
    }

}