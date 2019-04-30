/* Copyright Airship and Contributors */

package com.urbanairship.sample.utils;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

/**
 * The actions payload.
 */
public class ActionsPayload implements JsonSerializable {

    private List<String> tags;
    private String landingPageContent;
    private String openUrl;

    /**
     * Default constructor.
     *
     * @param builder The ActionsPayload builder instance.
     */
    private ActionsPayload(Builder builder) {
        tags = builder.tags;
        landingPageContent = builder.landingPageContent;
        openUrl = builder.openUrl;
    }

    /**
     * Creates a new Builder instance.
     *
     * @return The new Builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        JsonMap.Builder builder = JsonMap.newBuilder();

        if (!tags.isEmpty()) {
            builder.put("add_tag", JsonValue.wrapOpt(tags));
        }

        if (landingPageContent != null) {
            JsonMap contentPayload = JsonMap.newBuilder()
                                            .put("body", landingPageContent)
                                            .put("content-type", "text/html")
                                            .put("content_encoding", "utf-8")
                                            .build();

            JsonMap landingPageActionPayload = JsonMap.newBuilder()
                                                      .putOpt("type", "landing_page")
                                                      .putOpt("content", contentPayload)
                                                      .build();

            builder.put("open", landingPageActionPayload);
        }

        if (openUrl != null) {
            JsonMap openPayload = JsonMap.newBuilder()
                                         .putOpt("type", "url")
                                         .putOpt("content", openUrl)
                                         .build();

            builder.put("open", openPayload);
        }

        return builder.build().toJsonValue();

    }

    /**
     * Builds the ActionsPayload object.
     */
    public static class Builder {

        private List<String> tags = new ArrayList<>();

        private String landingPageContent;
        private String openUrl;

        private Builder() {}

        /**
         * Adds a tag.
         *
         * @param tag The tag.
         * @return The builder object.
         */
        public Builder addTag(String tag) {
            tags.add(tag);
            return this;
        }

        /**
         * Sets the landing page.
         *
         * @param content The content.
         * @return The builder object.
         */
        public Builder setLandingPage(String content) {
            landingPageContent = content;
            return this;
        }

        /**
         * Sets the open external URL.
         *
         * @param url The URL.
         * @return The builder object.
         */
        public Builder setOpenUrl(String url) {
            openUrl = url;
            return this;
        }

        /**
         * Creates the ActionsPayload.
         *
         * @return The ActionsPayload.
         */
        public ActionsPayload build() {
            return new ActionsPayload(this);
        }

    }

}
