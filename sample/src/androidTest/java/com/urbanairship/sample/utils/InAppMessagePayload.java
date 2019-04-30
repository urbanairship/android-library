/* Copyright Airship and Contributors */

package com.urbanairship.sample.utils;

import android.support.annotation.NonNull;

import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.HashMap;
import java.util.Map;

/**
 * The in-app message payload.
 */
public class InAppMessagePayload implements JsonSerializable {

    private String alert;
    private String interactiveType;
    private Map<String, ActionsPayload> buttonActions;
    private ActionsPayload openActions;

    /**
     * Default constructor.
     *
     * @param builder The InAppMessagePayload builder instance.
     */
    private InAppMessagePayload(Builder builder) {
        this.alert = builder.alert;
        this.interactiveType = builder.interactiveType;
        this.buttonActions = builder.buttonActions;
        this.openActions = builder.openActions;
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

        JsonMap.Builder payloadBuilder = JsonMap.newBuilder()
                                                .putOpt("alert", alert)
                                                .putOpt("display_type", "banner")
                                                .putOpt("actions", openActions);

        if (interactiveType != null) {
            JsonMap.Builder interactiveBuilder = JsonMap.newBuilder()
                                                        .putOpt("type", interactiveType);

            if (!buttonActions.isEmpty()) {
                interactiveBuilder.putOpt("button_actions", JsonValue.wrapOpt(buttonActions));
            }

            payloadBuilder.putOpt("interactive", interactiveBuilder.build());
        }

        return payloadBuilder.build().toJsonValue();
    }

    /**
     * Get the alert.
     *
     * @return The alert string.
     */
    public String getAlert() {
        return alert;
    }

    /**
     * Builds the InAppMessagePayload object.
     */
    public static class Builder {

        private String alert;
        private String interactiveType;
        private Map<String, ActionsPayload> buttonActions = new HashMap<>();
        private ActionsPayload openActions;

        private Builder() {}

        /**
         * Set the alert.
         *
         * @param alert The alert string.
         * @return The builder object.
         */
        public Builder setAlert(String alert) {
            this.alert = alert;
            return this;
        }

        /**
         * Set the interactive type.
         *
         * @param interactiveType The interactive type string.
         * @return The builder object.
         */
        public Builder setInteractiveType(String interactiveType) {
            this.interactiveType = interactiveType;
            return this;
        }

        /**
         * Add button actions.
         *
         * @param buttonIdentifier The button identifier string.
         * @param payload The actions payload.
         * @return The builder object.
         */
        public Builder addButtonActions(String buttonIdentifier, ActionsPayload payload) {
            buttonActions.put(buttonIdentifier, payload);
            return this;
        }

        /**
         * Set open actions.
         *
         * @param payload The actions payload.
         * @return The builder object.
         */
        public Builder setOpenActions(ActionsPayload payload) {
            this.openActions = payload;
            return this;
        }

        /**
         * Creates the InAppMessagePayload.
         *
         * @return The InAppMessagePayload.
         */
        public InAppMessagePayload build() {
            return new InAppMessagePayload(this);
        }

    }

}
