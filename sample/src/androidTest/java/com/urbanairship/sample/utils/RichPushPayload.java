/* Copyright Airship and Contributors */

package com.urbanairship.sample.utils;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

/**
 * The rich push payload.
 */
public class RichPushPayload implements JsonSerializable {

    private String title;
    private String content;

    /**
     * Default constructor.
     *
     * @param builder The RichPushPayload builder instance.
     */
    private RichPushPayload(Builder builder) {
        title = builder.title;
        content = builder.content;
    }

    /**
     * Creates a new Builder instance.
     *
     * @return The new Builder instance.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Get content.
     *
     * @return The content string.
     */
    public String getContent() {
        return content;
    }

    /**
     * Get title.
     *
     * @return The title string.
     */
    public String getTitle() {
        return title;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt("title", title)
                      .putOpt("body", content)
                      .putOpt("content_type", "text/html")
                      .build()
                      .toJsonValue();
    }

    /**
     * Builds the RichPushPayload object.
     */
    public static class Builder {

        private String title;
        private String content;

        private Builder() {}

        /**
         * Sets the title.
         *
         * @param title The title string.
         * @return The builder object.
         */
        public Builder setTitle(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the HTML content.
         *
         * @param content The HTML content string.
         * @return The builder object.
         */
        public Builder setHtmlContent(String content) {
            this.content = content;
            return this;
        }

        /**
         * Creates the RichPushPayload.
         *
         * @return The RichPushPayload.
         */
        public RichPushPayload build() {
            return new RichPushPayload(this);
        }

    }

}
