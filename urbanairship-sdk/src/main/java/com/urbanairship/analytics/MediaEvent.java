/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.analytics;

import java.math.BigDecimal;

/**
 * A class that represents a custom media event for the application.
 */
public class MediaEvent {
    /**
     * The browsed event name.
     */
    public static final String BROWSED_CONTENT_EVENT = "browsed_content";

    /**
     * The consumed_content event name.
     */
    public static final String CONSUMED_CONTENT_EVENT = "consumed_content";

    /**
     * The starred_content event name.
     */
    public static final String STARRED_CONTENT_EVENT = "starred_content";

    /**
     * The shared_content event name.
     */
    public static final String SHARED_CONTENT_EVENT = "shared_content";

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
     * The type property.
     */
    private static final String TYPE = "type";

    /**
     * The feature property.
     */
    private static final String FEATURE = "feature";

    /**
     * The author property.
     */
    private static final String AUTHOR = "author";

    /**
     * The published date.
     */
    private static final String PUBLISHED_DATE = "published_date";

    /**
     * The source property.
     */
    private static final String SOURCE = "source";

    /**
     * The medium property.
     */
    private static final String MEDIUM = "medium";

    private String eventName;

    // CONSUMED_CONTENT_EVENT optional
    private BigDecimal value;

    // optional
    private String id;
    private String category;
    private String description;
    private String type;
    private boolean feature;
    private boolean featureSet;
    private String author;
    private String publishedDate;

    // SHARED_CONTENT_EVENT optional
    private String source;
    private String medium;

    private MediaEvent(String eventName, BigDecimal value) {
        this.eventName = eventName;
        this.value = value;
    }

    private MediaEvent(String eventName, String source, String medium) {
        this.eventName = eventName;
        this.source = source;
        this.medium = medium;
    }

    /**
     * Creates a starred content event.
     *
     * @return A MediaEvent.
     */
    public static MediaEvent createStarredEvent() {
        return new MediaEvent(STARRED_CONTENT_EVENT, null);
    }

    /**
     * Creates a shared content event.
     *
     * @return A MediaEvent.
     */
    public static MediaEvent createSharedEvent() {
        return new MediaEvent(SHARED_CONTENT_EVENT, null);
    }

    /**
     * Creates a shared content event.
     * </p>
     * If the source or medium exceeds 255 characters it will cause the event to be invalid.
     *
     * @param source The source as a string.
     * @param medium The medium as a string.
     * @return A MediaEvent.
     */
    public static MediaEvent createSharedEvent(String source, String medium) {
        return new MediaEvent(SHARED_CONTENT_EVENT, source, medium);
    }

    /**
     * Creates a consumed content event.
     *
     * @return A MediaEvent.
     */
    public static MediaEvent createConsumedEvent() {
        return new MediaEvent(CONSUMED_CONTENT_EVENT, null);
    }

    /**
     * Creates a browsed event.
     *
     * @return A MediaEvent.
     */
    public static MediaEvent createBrowsedEvent() {
        return new MediaEvent(BROWSED_CONTENT_EVENT, null);
    }

    /**
     * Creates a consumed content event.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event value as a BigDecimal.
     * @return A MediaEvent.
     */
    public static MediaEvent createConsumedEvent(BigDecimal value) {
        return new MediaEvent(CONSUMED_CONTENT_EVENT, value);
    }

    /**
     * Creates a consumed content event.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event value as a double. Must be a number.
     * @return A MediaEvent.
     * @throws NumberFormatException if the value is infinity or not a number.
     */
    public static MediaEvent createConsumedEvent(double value) {
        return new MediaEvent(CONSUMED_CONTENT_EVENT, BigDecimal.valueOf(value));
    }

    /**
     * Creates a consumed content event.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event value as a string. Must contain valid string representation of a big decimal.
     * @return A MediaEvent.
     * @throws NumberFormatException if the event value does not contain a valid string representation
     * of a big decimal.
     */
    public static MediaEvent createConsumedEvent(String value) {
        if (value == null || value.length() == 0) {
            return new MediaEvent(CONSUMED_CONTENT_EVENT, null);
        } else {
            return new MediaEvent(CONSUMED_CONTENT_EVENT, new BigDecimal(value));
        }
    }

    /**
     * Creates a consumed content event.
     * <p/>
     * The event's value will be accurate 6 digits after the decimal. The number must fall in the
     * range [-2^31, 2^31-1]. Any value outside that range will cause the event to be invalid.
     *
     * @param value The event value as an int.
     * @return A MediaEvent.
     */
    public static MediaEvent createConsumedEvent(int value) {
        return new MediaEvent(CONSUMED_CONTENT_EVENT, new BigDecimal(value));
    }

    /**
     * Set the ID.
     * <p/>
     * If the ID exceeds 255 characters it will cause the event to be invalid.
     *
     * @param id The ID as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setId(String id) {
        this.id = id;
        return this;
    }

    /**
     * Set the category.
     * <p/>
     * If the category exceeds 255 characters it will cause the event to be invalid.
     *
     * @param category The category as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setCategory(String category) {
        this.category = category;
        return this;
    }

    /**
     * Set the type.
     * <p/>
     * If the type exceeds 255 characters it will cause the event to be invalid.
     *
     * @param type The type as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setType(String type) {
        this.type = type;
        return this;
    }

    /**
     * Set the description.
     * <p/>
     * If the description exceeds 255 characters it will cause the event to be invalid.
     *
     * @param description The description as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * Set the feature.
     *
     * @param feature The feature as a boolean.
     * @return A MediaEvent.
     */
    public MediaEvent setFeature(boolean feature) {
        this.feature = feature;
        this.featureSet = true;
        return this;
    }

    /**
     * Set the author.
     * <p/>
     * If the author exceeds 255 characters it will cause the event to be invalid.
     *
     * @param author The author as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setAuthor(String author) {
        this.author = author;
        return this;
    }

    /**
     * Set the publishedDate.
     * <p/>
     * If the publishedDate exceeds 255 characters it will cause the event to be invalid.
     *
     * @param publishedDate The publishedDate as a string.
     * @return A MediaEvent.
     */
    public MediaEvent setPublishedDate(String publishedDate) {
        this.publishedDate = publishedDate;
        return this;
    }

    /**
     * Creates and records the custom media event.
     */
    public CustomEvent track() {
        CustomEvent.Builder builder = new CustomEvent.Builder(this.eventName);

        if (this.value != null) {
            builder.setEventValue(this.value);
            builder.addProperty(LIFETIME_VALUE, true);
        } else {
            builder.addProperty(LIFETIME_VALUE, false);
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

        if (this.type != null) {
            builder.addProperty(TYPE, this.type);
        }

        if (this.featureSet) {
            builder.addProperty(FEATURE, this.feature);
        }

        if (this.author != null) {
            builder.addProperty(AUTHOR, this.author);
        }

        if (this.publishedDate != null) {
            builder.addProperty(PUBLISHED_DATE, this.publishedDate);
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
