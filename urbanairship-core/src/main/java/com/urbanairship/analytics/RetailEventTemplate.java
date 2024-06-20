/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class that represents a custom retail event template for the application.
 */
public class RetailEventTemplate {

    /**
     * The retail event template type.
     */
    @NonNull
    public static final String RETAIL_EVENT_TEMPLATE = "retail";

    /**
     * The browsed event name.
     */
    @NonNull
    public static final String BROWSED_PRODUCT_EVENT = "browsed";

    /**
     * The added_to_cart event name.
     */
    @NonNull
    public static final String ADDED_TO_CART_EVENT = "added_to_cart";

    /**
     * The starred_product event name.
     */
    @NonNull
    public static final String STARRED_PRODUCT_EVENT = "starred_product";

    /**
     * The shared_product event name.
     */
    @NonNull
    public static final String SHARED_PRODUCT_EVENT = "shared_product";

    /**
     * The purchased event name.
     */
    @NonNull
    public static final String PURCHASED_EVENT = "purchased";

    /**
     * The wishlist event name.
     */
    @NonNull
    public static final String WISHLIST_EVENT = "wishlist";

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

    /**
     * The wishlist name property.
     */
    private static final String WISHLIST_NAME = "wishlist_name";

    /**
     * The wishlist ID property.
     */
    private static final String WISHLIST_ID = "wishlist_id";

    /**
     * The currency property.
     */
    private static final String CURRENCY = "currency";

    @NonNull
    private final String eventName;

    @Nullable
    private BigDecimal value;

    @Nullable
    private String transactionId;

    @Nullable
    private String id;

    @Nullable
    private String category;

    @Nullable
    private String description;

    @Nullable
    private String brand;

    @Nullable
    private String source;

    @Nullable
    private String medium;

    @Nullable
    private String wishlistName;

    @Nullable
    private String wishlistId;

    @Nullable
    private String currency;

    private boolean newItem;
    private boolean newItemSet;

    private RetailEventTemplate(@NonNull String eventName) {
        this.eventName = eventName;
    }

    private RetailEventTemplate(@NonNull String eventName, @Nullable String source, @Nullable String medium,
                                @Nullable String wishlistName, @Nullable String wishlistId) {
        this.eventName = eventName;
        this.source = source;
        this.medium = medium;
        this.wishlistName = wishlistName;
        this.wishlistId = wishlistId;
    }

    /**
     * Creates a browsed event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newBrowsedTemplate() {
        return new RetailEventTemplate(BROWSED_PRODUCT_EVENT);
    }

    /**
     * Creates an added to cart event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newAddedToCartTemplate() {
        return new RetailEventTemplate(ADDED_TO_CART_EVENT);
    }

    /**
     * Creates a starred product event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newStarredProductTemplate() {
        return new RetailEventTemplate(STARRED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newSharedProductTemplate() {
        return new RetailEventTemplate(SHARED_PRODUCT_EVENT);
    }

    /**
     * Creates a shared product event template.
     *
     * @param source The source as a string.
     * @param medium The medium as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newSharedProductTemplate(@Nullable String source, @Nullable String medium) {
        return new RetailEventTemplate(SHARED_PRODUCT_EVENT, source, medium, null, null);
    }

    /**
     * Creates a wishlist event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newWishlistTemplate() {
        return new RetailEventTemplate(WISHLIST_EVENT);
    }
    /**
     * Creates a wishlist event template.
     *
     * @param name The wishlist name.
     * @param id The wishlist ID.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newWishlistTemplate(@Nullable String name, @Nullable String id) {
        return new RetailEventTemplate(WISHLIST_EVENT, null, null, name, id);
    }

    /**
     * Creates a purchased event template.
     *
     * @return A RetailEventTemplate.
     */
    @NonNull
    public static RetailEventTemplate newPurchasedTemplate() {
        return new RetailEventTemplate(PURCHASED_EVENT);
    }

    /**
     * Set the transaction ID.
     * <p>
     * If the transaction ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param transactionId The event's transaction ID as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setTransactionId(@Nullable String transactionId) {
        this.transactionId = transactionId;
        return this;
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a BigDecimal.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setValue(@Nullable BigDecimal value) {
        this.value = value;
        return this;
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a double. Must be a number.
     * @return A RetailEventTemplate.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    @NonNull
    public RetailEventTemplate setValue(double value) {
        return setValue(BigDecimal.valueOf(value));
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a string. Must contain valid string representation of a big decimal.
     * @return An RetailEventTemplate.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    @NonNull
    public RetailEventTemplate setValue(@Nullable String value) {
        if (value == null || value.length() == 0) {
            this.value = null;
            return this;
        }

        return setValue(new BigDecimal(value));
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as an int.
     * @return An RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setValue(int value) {
        return setValue(new BigDecimal(value));
    }

    /**
     * Set the ID.
     *
     * @param id The ID as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setId(@Nullable String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the category.
     *
     * @param category The category as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setCategory(@Nullable String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the description.
     *
     * @param description The description as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setDescription(@Nullable String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the currency.
     *
     * @param currency The currency name.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setCurrency(@Nullable String currency) {
        this.currency = currency;
        return this;
    }

    /**
     * Set the brand.
     *
     * @param brand The brand as a string.
     * @return A RetailEventTemplate.
     */
    @NonNull
    public RetailEventTemplate setBrand(@Nullable String brand) {
        this.brand = brand;
        return this;
    }

    /**
     * Set the newItem value.
     *
     * @param newItem A boolean value indicating if the item is new or not.
     * @return A RetailEventTemplate.
     */
    @NonNull
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
    @NonNull
    public CustomEvent createEvent() {
        CustomEvent.Builder builder = CustomEvent.newBuilder(this.eventName);

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

        if (this.wishlistName != null) {
            builder.addProperty(WISHLIST_NAME, this.wishlistName);
        }

        if (this.wishlistId != null) {
            builder.addProperty(WISHLIST_ID, this.wishlistId);
        }

        if (this.currency != null) {
            builder.addProperty(CURRENCY, this.currency);
        }

        builder.setTemplateType(RETAIL_EVENT_TEMPLATE);

        return builder.build();
    }

}
