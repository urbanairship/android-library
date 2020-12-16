/* Copyright Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A class that represents a custom search event template for the application.
 */
public class SearchEventTemplate {

    /**
     * The search event template type.
     */
    @NonNull
    private static final String SEARCH_EVENT_TEMPLATE = "search";

    /**
     * The search event name.
     */
    @NonNull
    private static final String SEARCH_EVENT = "search";

    /**
     * The lifetime value property.
     */
    private static final String LIFETIME_VALUE = "ltv";

    /**
     * The type property.
     */
    private static final String TYPE = "type";

    /**
     * The query property.
     */
    private static final String QUERY = "query";

    /**
     * The category property.
     */
    private static final String CATEGORY = "category";

    /**
     * The ID property.
     */
    private static final String ID = "id";

    /**
     * The total results property.
     */
    private static final String TOTAL_RESULTS = "total_results";

    @NonNull
    private final String eventName;

    @Nullable
    private BigDecimal value;

    @Nullable
    private String type;

    @Nullable
    private String query;

    @Nullable
    private String category;

    @Nullable
    private String id;

    private long total_results;

    private SearchEventTemplate (@NonNull String eventName) {
        this.eventName = eventName;
    }

    public static SearchEventTemplate newSearchTemplate() {
        return new SearchEventTemplate(SEARCH_EVENT);
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a BigDecimal.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setValue(@Nullable BigDecimal value) {
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
     * @return An SearchEventTemplate.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    @NonNull
    public SearchEventTemplate setValue(double value) {
        return setValue(BigDecimal.valueOf(value));
    }

    /**
     * Sets the event value.
     * <p>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event's value as a string. Must contain valid string representation of a big decimal.
     * @return An SearchEventTemplate.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    @NonNull
    public SearchEventTemplate setValue(@Nullable String value) {
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
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setValue(int value) {
        return setValue(new BigDecimal(value));
    }

    /**
     * Set the type.
     *
     * @param type The type as a string.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Set the query.
     *
     * @param query The query as a string.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setQuery(String query) {
        this.query = query;
        return this;
    }

    /**
     * Set the category.
     *
     * @param category The category as a string.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setCategory(String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the ID.
     *
     * @param id The ID as a string.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the total results.
     *
     * @param totalResults The total results as a string.
     * @return An SearchEventTemplate.
     */
    @NonNull
    public SearchEventTemplate setTotalResults(long totalResults) {
        this.total_results = totalResults;
        return this;
    }

    /**
     * Creates the custom search event.
     *
     * @return The custom search event.
     */
    @NonNull
    public CustomEvent createEvent() {
        CustomEvent.Builder builder = CustomEvent.newBuilder(this.eventName);

        if (this.value != null) {
            builder.setEventValue(this.value);
            builder.addProperty(LIFETIME_VALUE, true);
        } else {
            builder.addProperty(LIFETIME_VALUE, false);
        }

        if (this.type != null) {
            builder.addProperty(TYPE, this.type);
        }

        if (this.query != null) {
            builder.addProperty(QUERY, this.query);
        }

        if (this.category != null) {
            builder.addProperty(CATEGORY, this.category);
        }

        if (this.id != null) {
            builder.addProperty(ID, this.id);
        }

        builder.addProperty(TOTAL_RESULTS, this.total_results);

        builder.setTemplateType(SEARCH_EVENT_TEMPLATE);

        return builder.build();
    }
}
