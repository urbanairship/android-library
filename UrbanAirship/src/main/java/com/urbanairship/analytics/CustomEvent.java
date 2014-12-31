/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.analytics;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;
import com.urbanairship.richpush.RichPushMessage;
import com.urbanairship.util.UAStringUtil;

import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigDecimal;

/**
 * A class that represents a custom event for the application.
 */
public class CustomEvent extends Event {
    /**
     * The event type.
     */
    private static final String TYPE = "custom_event";

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
     * Last send id key.
     */
    public static final String LAST_RECEIVED_SEND_ID = "last_received_send_id";

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
    private static final int MAX_CHARACTER_LENGTH = 255;

    private String eventName;
    private Long eventValue;
    private String transactionId;
    private String interactionType;
    private String interactionId;
    private String sendId;

    private CustomEvent() {
    }

    @Override
    public final String getType() {
        return TYPE;
    }

    @Override
    protected final JSONObject getEventData() {
        JSONObject data = new JSONObject();

        String conversionSendId = UAirship.shared().getAnalytics().getConversionSendId();

        try {
            data.putOpt(EVENT_NAME, eventName);
            data.putOpt(EVENT_VALUE, eventValue);
            data.putOpt(INTERACTION_ID, interactionId);
            data.putOpt(INTERACTION_TYPE, interactionType);
            data.putOpt(TRANSACTION_ID, transactionId);

            if (!UAStringUtil.isEmpty(sendId)) {
                data.putOpt(CONVERSION_SEND_ID, sendId);
            } else if (conversionSendId != null) {
                data.putOpt(CONVERSION_SEND_ID, conversionSendId);
            } else {
                data.putOpt(LAST_RECEIVED_SEND_ID, UAirship.shared().getPushManager().getLastReceivedSendId());
            }

        } catch (JSONException e) {
            Logger.error("CustomEvent - Error constructing JSON data.", e);
        }

        return data;
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

        /**
         * Creates a new custom event builder.
         *
         * @param eventName The name of the event.
         * @throws java.lang.IllegalArgumentException if the event name is null, empty, or exceeds 255
         * characters.
         */
        public Builder(String eventName) {
            if (UAStringUtil.isEmpty(eventName)) {
                throw new IllegalArgumentException("Event name must not be null or empty.");
            }

            if (eventName.length() > MAX_CHARACTER_LENGTH) {
                throw new IllegalArgumentException("Event name is larger than 255 characters.");
            }

            this.eventName = eventName;
        }

        /**
         * Sets the event value.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1].
         *
         * @param value The event's value as a BigDecimal.
         * @return The custom event builder.
         * @throws java.lang.IllegalArgumentException if the event value is not within the valid
         * value range [-2^31, 2^31-1].
         */
        public Builder setEventValue(BigDecimal value) {
            if (value == null) {
                this.value = null;
                return this;
            }

            if (value.compareTo(MAX_VALUE) > 0) {
                throw new IllegalArgumentException("The value is bigger than " + MAX_VALUE);
            } else if (value.compareTo(MIN_VALUE) < 0) {
                throw new IllegalArgumentException("The value is less than " + MIN_VALUE);
            }

            this.value = value;

            return this;
        }

        /**
         * Sets the event value.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1]. Numbers outside the range are undefined and may prevent the event
         * from being created.
         *
         * @param value The event's value as a double
         * @return The custom event builder.
         * @throws java.lang.NumberFormatException if the value is infinity or not a number.
         * @throws java.lang.IllegalArgumentException if the value is not within the range [-2^31, 2^31-1].
         */
        public Builder setEventValue(double value) {
            return setEventValue(BigDecimal.valueOf(value));
        }

        /**
         * Sets the event value from a String.
         * <p/>
         * The event's value will be accurate 6 digits after the decimal. The number must fall in the
         * range [-2^31, 2^31-1].
         *
         * @param value The event's value as a String.
         * @return The custom event builder.
         * @throws java.lang.IllegalArgumentException if the event value is not within the range [-2^31, 2^31-1].
         * @throws java.lang.NumberFormatException if the event value does not contain a valid string representation
         * of a big decimal.
         */
        public Builder setEventValue(String value) {
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
         *
         * @param transactionId The event's transaction ID.
         * @return The custom event builder.
         * @throws java.lang.IllegalArgumentException if the transaction ID exceeds 255 characters.
         */
        public Builder setTransactionId(String transactionId) {
            if (transactionId != null && transactionId.length() > MAX_CHARACTER_LENGTH) {
                throw new IllegalArgumentException("Transaction ID is larger than 255 characters.");
            }
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
         *
         * @param interactionType The event's interaction type.
         * @param interactionId The event's interaction ID.
         * @return The custom event builder.
         * @throws java.lang.IllegalArgumentException if the interaction ID or type exceeds 255 characters.
         */
        public Builder setInteraction(String interactionType, String interactionId) {
            if (interactionId != null && interactionId.length() > MAX_CHARACTER_LENGTH) {
                throw new IllegalArgumentException("Interaction ID is larger than 255 characters.");
            }

            if (interactionType != null && interactionType.length() > MAX_CHARACTER_LENGTH) {
                throw new IllegalArgumentException("Interaction type is larger than 255 characters.");
            }

            this.interactionId = interactionId;
            this.interactionType = interactionType;
            return this;
        }

        /**
         * Sets the attribution from a specific push message.
         * @param pushMessage The attributing push message.
         * @return The custom event builder.
         *
         * @hide
         */
        public Builder setAttribution(PushMessage pushMessage) {
            if (pushMessage != null) {
                pushSendId = pushMessage.getSendId();
            }
            return this;
        }

        /**
         * Creates the custom event.
         *
         * @return The created custom event.
         */
        public CustomEvent create() {
            CustomEvent event = new CustomEvent();
            event.eventName = eventName;
            event.eventValue = value == null ? null : value.movePointRight(6).longValue();
            event.transactionId = transactionId;
            event.interactionType = interactionType;
            event.interactionId = interactionId;
            event.sendId = pushSendId;
            return event;
        }

        /**
         * Create the custom event and adds the event to Analytics.
         *
         * @return The created custom event.
         */
        public CustomEvent addEvent() {
            CustomEvent event = create();
            UAirship.shared().getAnalytics().addEvent(event);
            return event;
        }
    }
}
