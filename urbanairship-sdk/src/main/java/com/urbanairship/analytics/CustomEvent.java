/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.UAStringUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A class that represents a custom event for the application.
 */
public class CustomEvent extends Event implements JsonSerializable {
    /**
     * The event type.
     */
    static final String TYPE = "custom_event";

    /**
     * The interaction ID key.
     */
    public static final String INTERACTION_ID = "interaction_id";

    /**
     * The interaction type key.
     */
    public static final String INTERACTION_TYPE = "interaction_type";

    /**
     * The event name key.
     */
    public static final String EVENT_NAME = "event_name";

    /**
     * The event value key.
     */
    public static final String EVENT_VALUE = "event_value";

    /**
     * The event transaction id key.
     */
    public static final String TRANSACTION_ID = "transaction_id";

    /**
     * Rich Push Message interaction type.
     */
    public static final String MCRAP_TRANSACTION_TYPE = "ua_mcrap";

    /**
     * Hard conversion send id key.
     */
    public static final String CONVERSION_SEND_ID = "conversion_send_id";

    /**
     * Hard conversion send metadata key.
     */
    public static final String CONVERSION_METADATA = "conversion_metadata";

    /**
     * Last send metadata key.
     */
    public static final String LAST_RECEIVED_METADATA = "last_received_metadata";

    /**
     * The template type key.
     */
    public static final String TEMPLATE_TYPE = "template_type";

    /**
     * The custom properties key.
     */
    public static final String PROPERTIES = "properties";

    /**
     * Max value allowed for the event value before it is converted to a long.
     */
    private static final BigDecimal MAX_VALUE = new BigDecimal(Integer.MAX_VALUE);

    /**
     * Min value allowed for the event value before it is converted to a long.
     */
    private static final BigDecimal MIN_VALUE = new BigDecimal(Integer.MIN_VALUE);

    /**
     * The max size for any String event value.
     */
    public static final int MAX_CHARACTER_LENGTH = 255;

    /**
     * The max number of custom properties.
     */
    public static final int MAX_PROPERTIES = 100;

    /**
     * The max size of a collection that is allowed in a custom property.
     */
    public static final int MAX_PROPERTY_COLLECTION_SIZE = 20;


    private final String eventName;
    private final BigDecimal eventValue;
    private final String transactionId;
    private final String interactionType;
    private final String interactionId;
    private final String sendId;
    private final String metadata;
    private final String templateType;
    private final Map<String, Object> properties;


    private CustomEvent(Builder builder) {
        this.eventName = builder.eventName;
        this.eventValue = builder.value;
        this.transactionId = UAStringUtil.isEmpty(builder.transactionId) ? null : builder.transactionId;
        this.interactionType = UAStringUtil.isEmpty(builder.interactionType) ? null : builder.interactionType;
        this.interactionId = UAStringUtil.isEmpty(builder.interactionId) ? null : builder.interactionId;
        this.sendId = builder.pushSendId;
        this.metadata = builder.pushMetadata;
        this.templateType = builder.templateType;
        this.properties = new HashMap<>(builder.properties);
    }

    /**
     * Gets the event name.
     *
     * @return The event name.
     */
    public String getEventName() {
        return eventName;
    }

    /**
     * Gets the event value.
     *
     * @return The event value.
     */
    public BigDecimal getEventValue() {
        return eventValue;
    }

    /**
     * Gets the transaction ID.
     *
     * @return The transaction ID.
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Gets the interaction type.
     *
     * @return The interaction type.
     */
    public String getInteractionType() {
        return interactionType;
    }

    /**
     * Gets the interaction ID.
     *
     * @return The interaction ID.
     */
    public String getInteractionId() {
        return interactionId;
    }

    /**
     * Gets the event properties.
     *
     * @return The properties.
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JsonMap getEventData() {
        JsonMap.Builder data = JsonMap.newBuilder();

        String conversionSendId = UAirship.shared().getAnalytics().getConversionSendId();
        String conversionMetadata = UAirship.shared().getAnalytics().getConversionMetadata();

        data.put(EVENT_NAME, eventName);
        data.put(INTERACTION_ID, interactionId);
        data.put(INTERACTION_TYPE, interactionType);
        data.put(TRANSACTION_ID, transactionId);
        data.put(TEMPLATE_TYPE, templateType);

        if (eventValue != null) {
            data.put(EVENT_VALUE, eventValue.movePointRight(6).longValue());
        }

        if (!UAStringUtil.isEmpty(sendId)) {
            data.put(CONVERSION_SEND_ID, sendId);
        } else {
            data.put(CONVERSION_SEND_ID, conversionSendId);
        }

        if (!UAStringUtil.isEmpty(metadata)) {
            data.put(CONVERSION_METADATA, metadata);
        } else if (conversionMetadata != null) {
            data.put(CONVERSION_METADATA, conversionMetadata);
        } else {
            data.put(LAST_RECEIVED_METADATA, UAirship.shared().getPushManager().getLastReceivedMetadata());
        }

        JsonMap.Builder propertiesPayload = JsonMap.newBuilder();

        // Properties
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getValue() instanceof Collection) {
                propertiesPayload.put(entry.getKey(), JsonValue.wrapOpt(entry.getValue()).getList());
            } else {
                // Everything else can be stringified
                propertiesPayload.putOpt(entry.getKey(), JsonValue.wrapOpt(entry.getValue()).toString());
            }
        }

        if (propertiesPayload.build().getMap().size() > 0) {
            data.put(PROPERTIES, propertiesPayload.build());
        }

        return data.build();
    }

    @Override
    public JsonValue toJsonValue() {
        JsonMap.Builder data = JsonMap.newBuilder()
                                      .put(EVENT_NAME, eventName)
                                      .put(INTERACTION_ID, interactionId)
                                      .put(INTERACTION_TYPE, interactionType)
                                      .put(TRANSACTION_ID, transactionId)
                                      .put(PROPERTIES, JsonValue.wrapOpt(properties));

        if (eventValue != null) {
            data.putOpt(EVENT_VALUE, eventValue.doubleValue());
        }

        return data.build().toJsonValue();
    }

    @Override
    public boolean isValid() {

        boolean isValid = true;
        if (UAStringUtil.isEmpty(eventName) || eventName.length() > MAX_CHARACTER_LENGTH) {
            Logger.error("Event name must not be null, empty, or larger than " + MAX_CHARACTER_LENGTH + " characters.");
            isValid = false;
        }

        if (eventValue != null) {
            if (eventValue.compareTo(MAX_VALUE) > 0) {
                Logger.error("Event value is bigger than " + MAX_VALUE);
                isValid = false;
            } else if (eventValue.compareTo(MIN_VALUE) < 0) {
                Logger.error("Event value is smaller than " + MIN_VALUE);
                isValid = false;
            }
        }

        if (transactionId != null && transactionId.length() > MAX_CHARACTER_LENGTH) {
            Logger.error("Transaction ID is larger than " + MAX_CHARACTER_LENGTH + " characters.");
            isValid = false;
        }

        if (interactionId != null && interactionId.length() > MAX_CHARACTER_LENGTH) {
            Logger.error("Interaction ID is larger than " + MAX_CHARACTER_LENGTH + " characters.");
            isValid = false;
        }

        if (interactionType != null && interactionType.length() > MAX_CHARACTER_LENGTH) {
            Logger.error("Interaction type is larger than " + MAX_CHARACTER_LENGTH + " characters.");
            isValid = false;
        }

        if (templateType != null && templateType.length() > MAX_CHARACTER_LENGTH) {
            Logger.error("Template type is larger than " + MAX_CHARACTER_LENGTH + " characters.");
            isValid = false;
        }

        if (properties.size() > MAX_PROPERTIES) {
            Logger.error("Number of custom properties exceeds " + MAX_PROPERTIES);
            isValid = false;
        }

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().length() > MAX_CHARACTER_LENGTH) {
                Logger.error("The custom property " + entry.getKey() + " is larger than " + MAX_CHARACTER_LENGTH + " characters.");
                isValid = false;
            }

            if (entry.getValue() instanceof Collection) {
                Collection collection = (Collection) entry.getValue();
                if (collection.size() > MAX_PROPERTY_COLLECTION_SIZE) {
                    Logger.error("The custom property " + entry.getKey() + " contains a Collection<String> that is larger than  " + MAX_PROPERTY_COLLECTION_SIZE);
                    isValid = false;
                }

                for (Object object : collection) {
                    String string = String.valueOf(object);
                    if (string != null && string.length() > MAX_CHARACTER_LENGTH) {
                        Logger.error("The custom property " + entry.getKey() + " contains a value that is larger than  " + MAX_CHARACTER_LENGTH + " characters.");
                        isValid = false;
                    }
                }
            } else if (entry.getValue() instanceof String) {
                String stringValue = (String) entry.getValue();
                if (stringValue.length() > MAX_CHARACTER_LENGTH) {
                    Logger.error("The custom property " + entry.getKey() + " contains a value that is larger than  " + MAX_CHARACTER_LENGTH + " characters.");
                    isValid = false;
                }
            }
        }

        return isValid;
    }

    /**
     * Adds the event to Analytics.
     *
     * @return The tracked custom event.
     */
    public CustomEvent track() {
        UAirship.shared().getAnalytics().addEvent(this);
        return this;
    }

    /**
     * Builder class for {@link com.urbanairship.analytics.CustomEvent} Objects.
     */
    public static class Builder {

        private String eventName;
        private BigDecimal value;
        private String transactionId;
        private String interactionType;
        private String interactionId;
        private String pushSendId;
        private String pushMetadata;
        private String templateType;
        private Map<String, Object> properties = new HashMap<>();

        /**
         * Creates a new custom event builder
         * <p/>
         * The event name must be between 1 and 255 characters or the event will be invalid.
         *
         * @param eventName The name of the event.
         * @throws java.lang.IllegalArgumentException if the event name is null, empty, or exceeds 255
         * characters.
         */
        public Builder(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String eventName) {
            this.eventName = eventName;
        }

        /**
         * Sets the event value.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a BigDecimal.
         * @return The custom event builder.
         */
        public Builder setEventValue(@Nullable BigDecimal value) {
            if (value == null) {
                this.value = null;
                return this;
            }

            this.value = value;
            return this;
        }

        /**
         * Sets the event value.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a double
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the value is infinity or not a number.
         */
        public Builder setEventValue(double value) {
            return setEventValue(BigDecimal.valueOf(value));
        }

        /**
         * Sets the event value from a String.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
         *
         * @param value The event's value as a String.
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the event value does not contain a valid string representation
         * of a big decimal.
         */
        public Builder setEventValue(@Nullable String value) {
            if (UAStringUtil.isEmpty(value)) {
                this.value = null;
                return this;
            }

            return setEventValue(new BigDecimal(value));
        }

        /**
         * Sets the event value.
         *
         * @param value The event's value as an int.
         * @return The custom event builder.
         */
        public Builder setEventValue(int value) {
            return setEventValue(new BigDecimal(value));
        }

        /**
         * Sets the transaction ID.
         * <p/>
         * If the transaction ID exceeds 255 characters it will cause the event to be invalid.
         *
         * @param transactionId The event's transaction ID.
         * @return The custom event builder.
         */
        public Builder setTransactionId(@Size(min = 1, max = MAX_CHARACTER_LENGTH) String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        /**
         * Sets the interaction type and ID from a {@link com.urbanairship.richpush.RichPushMessage}.
         *
         * @param message The rich push message that created the custom event.
         * @return The custom event builder.
         */
        public Builder setInteraction(RichPushMessage message) {
            if (message != null) {
                this.interactionType = MCRAP_TRANSACTION_TYPE;
                this.interactionId = message.getMessageId();
            }
            return this;
        }

        /**
         * Sets the interaction type and ID for the event.
         * <p/>
         * If any field exceeds 255 characters it will cause the event to be invalid.
         *
         * @param interactionType The event's interaction type.
         * @param interactionId The event's interaction ID.
         * @return The custom event builder.
         */
        public Builder setInteraction(@Size(min = 1, max = MAX_CHARACTER_LENGTH) String interactionType,
                                      @Size(min = 1, max = MAX_CHARACTER_LENGTH) String interactionId) {

            this.interactionId = interactionId;
            this.interactionType = interactionType;
            return this;
        }

        /**
         * Sets the attribution from a specific push message.
         *
         * @param pushMessage The attributing push message.
         * @return The custom event builder.
         * @hide
         */
        public Builder setAttribution(PushMessage pushMessage) {
            if (pushMessage != null) {
                pushSendId = pushMessage.getSendId();
                pushMetadata = pushMessage.getMetadata();
            }
            return this;
        }

        /**
         * Sets the template type for the event.
         *
         * @param templateType The event's template type.
         * @return The custom event builder.
         */
        Builder setTemplateType(@Size(min = 1, max = MAX_CHARACTER_LENGTH) String templateType) {
            this.templateType = templateType;
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, or if the name or value
         * of the property exceeds {@link #MAX_CHARACTER_LENGTH} it will cause the event to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name,
                                   @NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, or if the name of the
         * property exceeds {@link #MAX_CHARACTER_LENGTH} it will cause the event to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name, int value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, or if the name of the
         * property exceeds {@link #MAX_CHARACTER_LENGTH} it will cause the event to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name, long value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, or if the name of the
         * property exceeds {@link #MAX_CHARACTER_LENGTH} it will cause the event to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the value is infinite or not a number
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name, double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new NumberFormatException("Infinity or NaN: " + value);
            }

            properties.put(name, value);
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, or if the name of the
         * property exceeds {@link #MAX_CHARACTER_LENGTH} it will cause the event to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name, boolean value) {
            properties.put(name, value);
            return this;
        }

        /**
         * Adds a custom property to the event.
         * <p/>
         * If the max number of properties exceeds {@link #MAX_PROPERTIES}, if the name of the
         * property, or any of the Strings within its value exceeds {@link #MAX_CHARACTER_LENGTH}, or
         * if the value contains more than {@link #MAX_PROPERTY_COLLECTION_SIZE} it will cause the event
         * to be invalid.
         *
         * @param name The property name.
         * @param value The property value.
         * @return The custom event builder.
         */
        public Builder addProperty(@NonNull @Size(min = 1, max = MAX_CHARACTER_LENGTH) String name,
                                   @NonNull @Size(min = 1, max = MAX_PROPERTY_COLLECTION_SIZE) Collection<String> value) {

            properties.put(name, new ArrayList<>(value));
            return this;
        }

        /**
         * Creates the custom event.
         *
         * @return The created custom event.
         */
        public CustomEvent create() {
            return new CustomEvent(this);
        }
    }
}
