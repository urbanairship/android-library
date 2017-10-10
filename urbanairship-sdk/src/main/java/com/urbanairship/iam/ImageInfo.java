/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.iam;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;
import com.urbanairship.util.UAStringUtil;

/**
 * Image display info.
 */
public class ImageInfo implements JsonSerializable {

    private static final String URL_KEY = "url";

    private final String url;

    /**
     * Default constructor.
     *
     * @param builder The image info builder.
     */
    private ImageInfo(Builder builder) {
        this.url = builder.url;
    }

    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .put(URL_KEY, url)
                      .build()
                      .toJsonValue();
    }

    /**
     * Parses an {@link ImageInfo} from a {@link JsonValue}.
     *
     * @param jsonValue The json value.
     * @return The parsed image info.
     * @throws JsonException If the image info was unable to be parsed.
     */
    public static ImageInfo parseJson(JsonValue jsonValue) throws JsonException {
        try {
            return newBuilder()
                    .setUrl(jsonValue.optMap().opt(URL_KEY).getString())
                    .build();
        } catch (IllegalArgumentException e) {
            throw new JsonException("Invalid image object json: " + jsonValue, e);
        }
    }

    /**
     * Returns the image URL.
     *
     * @return The image URL.
     */
    @NonNull
    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ImageInfo imageInfo = (ImageInfo) o;

        return url != null ? url.equals(imageInfo.url) : imageInfo.url == null;

    }

    @Override
    public int hashCode() {
        return url != null ? url.hashCode() : 0;
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
     * Image info builder.
     */
    public static class Builder {
        private String url;

        private Builder() {}

        /**
         * Sets the image URL.
         *
         * @param url The image URL.
         * @return The builder instance.
         */
        public Builder setUrl(String url) {
            this.url = url;
            return this;
        }

        /**
         * Builds the image info.
         *
         * @return An image info.
         * @throws IllegalArgumentException If the URL is missing.
         */
        public ImageInfo build() {
            Checks.checkArgument(!UAStringUtil.isEmpty(url), "Missing URL");
            return new ImageInfo(this);
        }
    }
}
