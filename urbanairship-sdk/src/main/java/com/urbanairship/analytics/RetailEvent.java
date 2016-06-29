/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

/**
 * A class that represents a custom retail event for the application.
 */
public class RetailEvent {
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

    private RetailEvent(String eventName) {
        this.eventName = eventName;
    }

    private RetailEvent(String eventName, String source, String medium) {
        this.eventName = eventName;
        this.source = source;
        this.medium = medium;
    }

    /**
     * Creates a browsed event.
     *
     * @return A RetailEvent.
     */
    public static RetailEvent createBrowsedEvent() {
        return new RetailEvent(BROWSED_PRODUCT_EVENT);
    }

    /**
     * Creates an added to cart event.
     *
     * @return A RetailEvent.
     */
    public static RetailEvent createAddedToCartEvent() {
        return new RetailEvent(ADDED_TO_CART_EVENT);
    }

    /**
     * Creates a starred product event.
     *
     * @return A RetailEvent.
     */
    public static RetailEvent createStarredProduct() {
        return new RetailEvent(STARRED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event.
     *
     * @return A RetailEvent.
     */
    public static RetailEvent createSharedProduct() {
        return new RetailEvent(SHARED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event.
     * <p/>
     * If the source or medium exceeds 255 characters it will cause the event to be invalid.
     *
     * @param source The source as a string.
     * @param medium The medium as a string.
     * @return A RetailEvent.
     */
    public static RetailEvent createSharedProduct(String source, String medium) {
        return new RetailEvent(SHARED_PRODUCT_EVENT, source, medium);
    }

    /**
     * Creates a purchased event.
     *
     * @return A RetailEvent.
     */
    public static RetailEvent createPurchasedEvent() {
        return new RetailEvent(PURCHASED_EVENT);
    }

    /**
     * Set the transaction ID.
     * <p/>
     * If the transaction ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param transactionId The event's transaction ID as a string.
     * @return A RetailEvent.
     */
    public RetailEvent setTransactionId(String transactionId) {
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
     * @return A RetailEvent.
     */
    public RetailEvent setValue(BigDecimal value) {
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
     * @return A RetailEvent.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    public RetailEvent setValue(double value) {
        return setValue(BigDecimal.valueOf(value));
    }

    /**
     * Sets the event value.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a string. Must contain valid string representation of a big decimal.
     * @return An AccountEvent.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    public RetailEvent setValue(String value) {
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
     * @return An AccountEvent.
     */
    public RetailEvent setValue(int value) {
        return setValue(new BigDecimal(value));
    }

    /**
     * Set the ID.
     * <p/>
     * If the ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param id The ID as a string.
     * @return A RetailEvent.
     */
    public RetailEvent setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the category.
     * <p/>
     * If the category exceeds 255 characters it will cause the event to be invalid.
     *
     * @param category The category as a string.
     * @return A RetailEvent.
     */
    public RetailEvent setCategory(String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the description.
     * <p/>
     * If the description exceeds 255 characters it will cause the event to be invalid.
     *
     * @param description The description as a string.
     * @return A RetailEvent.
     */
    public RetailEvent setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the brand.
     * <p/>
     * If the brand exceeds 255 characters it will cause the event to be invalid.
     *
     * @param brand The brand as a string.
     * @return A RetailEvent.
     */
    public RetailEvent setBrand(String brand) {
        this.brand = brand;
        return this;
    }

    /**
     * Set the newItem value.
     *
     * @param newItem A boolean value indicating if the item is new or not.
     * @return A RetailEvent.
     */
    public RetailEvent setNewItem(boolean newItem) {
        this.newItem = newItem;
        this.newItemSet = true;
        return this;
    }

    /**
     * Creates and records the custom retail event.
     */
    public CustomEvent track() {
        CustomEvent.Builder builder = new CustomEvent.Builder(this.eventName);

        if (this.value != null) {
            builder.setEventValue(this.value);
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

        return builder.addEvent();
    }
}
