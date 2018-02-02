/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;
import android.support.annotation.StringDef;

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
    public static final String TYPE_IMAGE = "image";

    /**
     * Video media type.
     */
    public static final String TYPE_VIDEO = "video";

    /**
     * Youtube media type.
     */
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
    private MediaInfo(Builder builder) {
        this.url = builder.url;
        this.description = builder.description;
        this.type = builder.type;
    }

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
     * Parses a {@link MediaInfo} from a {@link JsonValue}.
     *
     * @param jsonValue The json value.
     * @return The parsed media info.
     * @throws JsonException If the media info was unable to be parsed.
     */
    public static MediaInfo parseJson(JsonValue jsonValue) throws JsonException {
        try {
            return newBuilder()
                    .setUrl(jsonValue.optMap().opt(URL_KEY).getString())
                    .setType(jsonValue.optMap().opt(TYPE_KEY).getString())
                    .setDescription(jsonValue.optMap().opt(DESCRIPTION_KEY).getString())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid media object json: " + jsonValue, e);
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
    public boolean equals(Object o) {
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

    @Override
    public String toString() {
        return toJsonValue().toString();
    }

    /**
     * Builder factory method.
     *
     * @return A builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Media info builder.
     */
    public static class Builder {
        private String url;
        private String type;
        private String description;

        private Builder() {}

        /**
         * Sets the media URL.
         *
         * @param url The media URL.
         * @return The builder instance.
         */
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
        public MediaInfo build() {
            Checks.checkArgument(!UAStringUtil.isEmpty(url), "Missing URL");
            Checks.checkArgument(!UAStringUtil.isEmpty(type), "Missing type");
            Checks.checkArgument(!UAStringUtil.isEmpty(description), "Missing description");
            return new MediaInfo(this);
        }
    }
}
