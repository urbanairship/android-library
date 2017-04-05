/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

/**
 * A class that represents a custom retail event template for the application.
 */
public class RetailEventTemplate {

    /**
     * The retail event template type.
     */
    public static final String RETAIL_EVENT_TEMPLATE = "retail";

    /**
     * The browsed event name.
     */
    public static final String BROWSED_PRODUCT_EVENT = "browsed";

    /**
     * The added_to_cart event name.
     */
    public static final String ADDED_TO_CART_EVENT = "added_to_cart";

    /**
     * The starred_product event name.
     */
    public static final String STARRED_PRODUCT_EVENT = "starred_product";

    /**
     * The shared_product event name.
     */
    public static final String SHARED_PRODUCT_EVENT = "shared_product";

    /**
     * The purchased event name.
     */
    public static final String PURCHASED_EVENT = "purchased";

    /**
     * The lifetime value property.
     */
    private static final String LIFETIME_VALUE = "ltv";

    /**
     * The ID property.
     */
    private static final String ID = "id";

    /**
     * The category property.
     */
    private static final String CATEGORY = "category";

    /**
     * The description property.
     */
    private static final String DESCRIPTION = "description";

    /**
     * The brand property.
     */
    private static final String BRAND = "brand";

    /**
     * The new item property.
     */
    private static final String NEW_ITEM = "new_item";

    /**
     * The source property.
     */
    private static final String SOURCE = "source";

    /**
     * The medium property.
     */
    private static final String MEDIUM = "medium";

    private String eventName;

    // optional
    private BigDecimal value;
    private String transactionId;
    private String id;
    private String category;
    private String description;
    private String brand;
    private boolean newItem;
    private boolean newItemSet;
    private String source;
    private String medium;

    private RetailEventTemplate(String eventName) {
        this.eventName = eventName;
    }

    private RetailEventTemplate(String eventName, String source, String medium) {
        this.eventName = eventName;
        this.source = source;
        this.medium = medium;
    }

    /**
     * Creates a browsed event template.
     *
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newBrowsedTemplate() {
        return new RetailEventTemplate(BROWSED_PRODUCT_EVENT);
    }

    /**
     * Creates an added to cart event template.
     *
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newAddedToCartTemplate() {
        return new RetailEventTemplate(ADDED_TO_CART_EVENT);
    }

    /**
     * Creates a starred product event template.
     *
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newStarredProductTemplate() {
        return new RetailEventTemplate(STARRED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event template.
     *
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newSharedProductTemplate() {
        return new RetailEventTemplate(SHARED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event template.
     * <p/>
     * If the source or medium exceeds 255 characters it will cause the event to be invalid.
     *
     * @param source The source as a string.
     * @param medium The medium as a string.
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newSharedProductTemplate(String source, String medium) {
        return new RetailEventTemplate(SHARED_PRODUCT_EVENT, source, medium);
    }

    /**
     * Creates a purchased event template.
     *
     * @return A RetailEventTemplate.
     */
    public static RetailEventTemplate newPurchasedTemplate() {
        return new RetailEventTemplate(PURCHASED_EVENT);
    }

    /**
     * Set the transaction ID.
     * <p/>
     * If the transaction ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param transactionId The event's transaction ID as a string.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setTransactionId(String transactionId) {
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
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setValue(BigDecimal value) {
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
     * @return A RetailEventTemplate.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    public RetailEventTemplate setValue(double value) {
        return setValue(BigDecimal.valueOf(value));
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a string. Must contain valid string representation of a big decimal.
     * @return An RetailEventTemplate.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    public RetailEventTemplate setValue(String value) {
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
     * @return An RetailEventTemplate.
     */
    public RetailEventTemplate setValue(int value) {
        return setValue(new BigDecimal(value));
    }

    /**
     * Set the ID.
     * <p/>
     * If the ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param id The ID as a string.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the category.
     * <p/>
     * If the category exceeds 255 characters it will cause the event to be invalid.
     *
     * @param category The category as a string.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setCategory(String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the description.
     * <p/>
     * If the description exceeds 255 characters it will cause the event to be invalid.
     *
     * @param description The description as a string.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the brand.
     * <p/>
     * If the brand exceeds 255 characters it will cause the event to be invalid.
     *
     * @param brand The brand as a string.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setBrand(String brand) {
        this.brand = brand;
        return this;
    }

    /**
     * Set the newItem value.
     *
     * @param newItem A boolean value indicating if the item is new or not.
     * @return A RetailEventTemplate.
     */
    public RetailEventTemplate setNewItem(boolean newItem) {
        this.newItem = newItem;
        this.newItemSet = true;
        return this;
    }

    /**
     * Creates the custom retail event.
     *
     * @return The custom retail event.
     */
    public CustomEvent createEvent() {
        CustomEvent.Builder builder = new CustomEvent.Builder(this.eventName);

        if (this.value != null) {
            builder.setEventValue(this.value);
        }

        if (PURCHASED_EVENT.equals(this.eventName) && this.value != null) {
            builder.addProperty(LIFETIME_VALUE, true);
        } else {
            builder.addProperty(LIFETIME_VALUE, false);
        }

        if (this.transactionId != null) {
            builder.setTransactionId(this.transactionId);
        }

        if (this.id != null) {
            builder.addProperty(ID, this.id);
        }

        if (this.category != null) {
            builder.addProperty(CATEGORY, this.category);
        }

        if (this.description != null) {
            builder.addProperty(DESCRIPTION, this.description);
        }

        if (this.brand != null) {
            builder.addProperty(BRAND, this.brand);
        }

        if (this.newItemSet) {
            builder.addProperty(NEW_ITEM, this.newItem);
        }

        if (this.source != null) {
            builder.addProperty(SOURCE, this.source);
        }

        if (this.medium != null) {
            builder.addProperty(MEDIUM, this.medium);
        }

        builder.setTemplateType(RETAIL_EVENT_TEMPLATE);

        return builder.create();
    }
}
