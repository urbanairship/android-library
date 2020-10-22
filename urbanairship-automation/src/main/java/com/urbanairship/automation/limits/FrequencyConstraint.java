/* Copyright Airship and Contributors */

package com.urbanairship.automation.limits;

import com.urbanairship.util.Checks;

import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

/**
 * Defines a frequency constraint.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class FrequencyConstraint {

    private final String id;
    private final long range;
    private final int count;

    private FrequencyConstraint(Builder builder) {
        this.id = builder.id;
        this.range = builder.range;
        this.count = builder.count;
    }

    /**
     * Gets the constraint time range in milliseconds.
     *
     * @return The constraint in milliseconds.
     */
    public long getRange() {
        return range;
    }

    /**
     * Gets the constraint Id.
     *
     * @return The id.
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * Gets the constraint count.
     *
     * @return The count.
     */
    public int getCount() {
        return count;
    }

    /**
     * Creates a new builder.
     *
     * @return The builder.
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * The constraint builder.
     */
    public static class Builder {

        private String id;
        private long range;
        private int count;

        private Builder() {}

        /**
         * Sets the constraint Id.
         *
         * @param id The id.
         * @return The builder.
         */
        @NonNull
        public Builder setId(@Nullable String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the constraint time range.
         *
         * @param unit The unit of time.
         * @param duration The duration.
         * @return The builder.
         */
        @NonNull
        public Builder setRange(@NonNull TimeUnit unit, long duration) {
            this.range = unit.toMillis(duration);
            return this;
        }

        /**
         * Sets the count.
         *
         * @param count The count.
         * @return The builder.
         */
        @NonNull
        public Builder setCount(int count) {
            this.count = count;
            return this;
        }

        /**
         * Builds the constraint.
         *
         * @return The frequency constraint.
         * @throws IllegalArgumentException if the id, range, or count is not set.
         */
        @NonNull
        public FrequencyConstraint build() {
            Checks.checkNotNull(id, "missing id");
            Checks.checkArgument(range > 0, "missing range");
            Checks.checkArgument(count > 0, "missing count");

            return new FrequencyConstraint(this);
        }

    }

}
