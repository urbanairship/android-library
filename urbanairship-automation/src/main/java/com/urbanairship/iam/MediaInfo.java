/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UAStringUtil;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Media display info.
 */
public class MediaInfo implements JsonSerializable {

    @StringDef({ TYPE_IMAGE, TYPE_VIDEO, TYPE_YOUTUBE })
    @Retention(RetentionPolicy.SOURCE)
    @interface Type {}

    /**
     * Image media type.
     */
    @NonNull
    public static final String TYPE_IMAGE = "image";

    /**
     * Video media type.
     */
    @NonNull
    public static final String TYPE_VIDEO = "video";

    /**
     * Youtube media type.
     */
    @NonNull
    public static final String TYPE_YOUTUBE = "youtube";

    // JSON keys
    private static final String URL_KEY = "url";
    private static final String DESCRIPTION_KEY = "description";
    private static final String TYPE_KEY = "type";

    private final String url;
    private final String description;
    private final String type;

    /**
     * Default constructor.
     *
     * @param builder The media info builder.
     */
    private MediaInfo(@NonNull Builder builder) {
        this.url = builder.url;
        this.description = builder.description;
        this.type = builder.type;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(URL_KEY, url)
                      .put(DESCRIPTION_KEY, description)
                      .put(TYPE_KEY, type)
                      .build()
                      .toJsonValue();
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed MediaInfo.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static MediaInfo parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
    }

    /**
     * Parses a {@link MediaInfo} from a {@link JsonValue}.
     *
     * @param value The json value.
     * @return The parsed media info.
     * @throws JsonException If the media info was unable to be parsed.
     */
    @NonNull
    public static MediaInfo fromJson(@NonNull JsonValue value) throws JsonException {
        try {
            return newBuilder()
                    .setUrl(value.optMap().opt(URL_KEY).optString())
                    .setType(value.optMap().opt(TYPE_KEY).optString())
                    .setDescription(value.optMap().opt(DESCRIPTION_KEY).optString())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid media object json: " + value, e);
        }
    }

    /**
     * Returns the media URL.
     *
     * @return The media URL.
     */
    @NonNull
    public String getUrl() {
        return url;
    }

    /**
     * Returns the media type.
     *
     * @return The media type.
     */
    @NonNull
    @Type
    public String getType() {
        return type;
    }

    /**
     * Returns the media description.
     *
     * @return The media description.
     */
    @NonNull
    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MediaInfo mediaInfo = (MediaInfo) o;

        if (url != null ? !url.equals(mediaInfo.url) : mediaInfo.url != null) {
            return false;
        }
        if (description != null ? !description.equals(mediaInfo.description) : mediaInfo.description != null) {
            return false;
        }
        return type != null ? type.equals(mediaInfo.type) : mediaInfo.type == null;
    }

    @Override
    public int hashCode() {
        int result = url != null ? url.hashCode() : 0;
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    /**
     * Builder factory method.
     *
     * @return A builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Creates a builder from existing media info.
     *
     * @param mediaInfo The media info.
     * @return A builder instance.
     */
    @NonNull
    public static Builder newBuilder(@NonNull MediaInfo mediaInfo) {
        return new Builder(mediaInfo);
    }

    /**
     * Media info builder.
     */
    public static class Builder {

        private String url;
        private String type;
        private String description;

        private Builder() {
        }

        private Builder(MediaInfo mediaInfo) {
            this.url = mediaInfo.url;
            this.description = mediaInfo.description;
            this.type = mediaInfo.type;
        }

        /**
         * Sets the media URL.
         *
         * @param url The media URL.
         * @return The builder instance.
         */
        @NonNull
        public Builder setUrl(@NonNull String url) {
            this.url = url;
            return this;
        }

        /**
         * Sets the media type.
         *
         * @param type The media type.
         * @return The builder instance.
         */
        @NonNull
        public Builder setType(@Type @NonNull String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the media description.
         *
         * @param description The media description.
         * @return The builder instance.
         */
        @NonNull
        public Builder setDescription(@NonNull String description) {
            this.description = description;
            return this;
        }

        /**
         * Builds the media info.
         *
         * @return A media info.
         * @throws IllegalArgumentException If the URL, type, or description is missing.
         */
        @NonNull
        public MediaInfo build() {
            Checks.checkArgument(!UAStringUtil.isEmpty(url), "Missing URL");
            Checks.checkArgument(!UAStringUtil.isEmpty(type), "Missing type");
            Checks.checkArgument(!UAStringUtil.isEmpty(description), "Missing description");
            return new MediaInfo(this);
        }

    }

}
