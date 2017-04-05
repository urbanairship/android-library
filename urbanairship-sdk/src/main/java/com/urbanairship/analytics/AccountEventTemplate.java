/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

/**
 * A class that represents a custom account event template for the application.
 */
public class AccountEventTemplate {

    /**
     * The account event template type.
     */
    public static final String ACCOUNT_EVENT_TEMPLATE = "account";

    /**
     * The registered account event name.
     */
    public static final String REGISTERED_ACCOUNT_EVENT = "registered_account";

    /**
     * The lifetime value property.
     */
    private static final String LIFETIME_VALUE = "ltv";

    /**
     * The category property.
     */
    private static final String CATEGORY = "category";

    // optional
    private BigDecimal value;
    private String category;
    private String transactionId;

    private AccountEventTemplate() {
    }

    /**
     * Creates a registered account event template.
     *
     * @return An AccountEventTemplate.
     */
    public static AccountEventTemplate newRegisteredTemplate() {
        return new AccountEventTemplate();
    }

    /**
     * Set the transaction ID.
     * <p/>
     * If the transaction ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param transactionId The event's transaction ID as a string.
     * @return An AccountEventTemplate.
     */
    public AccountEventTemplate setTransactionId(String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a BigDecimal.
     * @return An AccountEventTemplate.
     */
    public AccountEventTemplate setValue(BigDecimal value) {
        this.value = value;
        return this;
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a double. Must be a number.
     * @return An AccountEventTemplate.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    public AccountEventTemplate setValue(double value) {
        return setValue(BigDecimal.valueOf(value));
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a string. Must contain valid string representation of a big decimal.
     * @return An AccountEventTemplate.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    public AccountEventTemplate setValue(String value) {
        if (value == null || value.length() == 0) {
            this.value = null;
            return this;
        }

        return setValue(new BigDecimal(value));
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as an int.
     * @return An AccountEventTemplate.
     */
    public AccountEventTemplate setValue(int value) {
        return setValue(new BigDecimal(value));
    }

    /**
     * Set the category.
     * </p>
     * If the category exceeds 255 characters it will cause the event to be invalid.
     *
     * @param category The category as a string.
     * @return An AccountEventTemplate.
     */
    public AccountEventTemplate setCategory(String category) {
        this.category = category;
        return this;
    }

    /**
     * Creates the custom account event.
     *
     * @return The custom account event.
     */
    public CustomEvent createEvent() {
        CustomEvent.Builder builder = new CustomEvent.Builder(REGISTERED_ACCOUNT_EVENT);

        if (this.value != null) {
            builder.setEventValue(this.value);
            builder.addProperty(LIFETIME_VALUE, true);
        } else {
            builder.addProperty(LIFETIME_VALUE, false);
        }

        if (this.transactionId != null) {
            builder.setTransactionId(this.transactionId);
        }

        if (this.category != null) {
            builder.addProperty(CATEGORY, this.category);
        }

        builder.setTemplateType(ACCOUNT_EVENT_TEMPLATE);

        return builder.create();
    }
}
