/* Copyright Airship and Contributors */

package com.urbanairship.job;

import com.urbanairship.AirshipComponent;
import com.urbanairship.json.JsonMap;
import com.urbanairship.util.Checks;

import java.lang.annotation.Retention;
import java.util.concurrent.TimeUnit;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.ObjectsCompat;

import static java.lang.annotation.RetentionPolicy.SOURCE;

/**
 * Airship job for defining a unit of work to be performed in an {@link AirshipComponent}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobInfo {

    @IntDef({ JOB_FINISHED, JOB_RETRY })
    @Retention(SOURCE)
    public @interface JobResult {}

    /**
     * JobInfo is finished.
     */
    public static final int JOB_FINISHED = 0;

    /**
     * JobInfo needs to be retried at a later date.
     */
    public static final int JOB_RETRY = 1;


    @IntDef({ REPLACE, APPEND, KEEP })
    @Retention(SOURCE)
    public @interface ConflictStrategy {}

    public static final int REPLACE = 0;
    public static final int APPEND = 1;
    public static final int KEEP = 2;

    private final String action;
    private final String airshipComponentName;
    private final boolean isNetworkAccessRequired;
    private final long initialDelay;
    private final int conflictStrategy;
    private final long minInitialBackOffMs;
    private final JsonMap extras;

    /**
     * Default constructor.
     *
     * @param builder A builder instance.
     */
    private JobInfo(@NonNull Builder builder) {
        this.action = builder.action;
        this.airshipComponentName = builder.airshipComponentName == null ? "" : builder.airshipComponentName;
        this.extras = builder.extras != null ? builder.extras : JsonMap.EMPTY_MAP;
        this.isNetworkAccessRequired = builder.isNetworkAccessRequired;
        this.initialDelay = builder.initialDelay;
        this.conflictStrategy = builder.conflictStrategy;
        this.minInitialBackOffMs = builder.initialBackOffMs;
    }

    /**
     * The job's action.
     *
     * @return The job's action.
     */
    @NonNull
    public String getAction() {
        return action;
    }

    /**
     * If network access is required for the job.
     *
     * @return {@code true} if network access is required, otherwise {@code false}.
     */
    public boolean isNetworkAccessRequired() {
        return isNetworkAccessRequired;
    }

    /**
     * Gets the initial delay in milliseconds.
     *
     * @return The initial delay in milliseconds.
     */
    public long getInitialDelay() {
        return initialDelay;
    }

    /**
     * The job's extras.
     *
     * @return The job's extras.
     */
    @NonNull
    public JsonMap getExtras() {
        return extras;
    }

    /**
     * The {@link AirshipComponent} name that will receive the job.
     *
     * @return The {@link AirshipComponent} class name.
     */
    @NonNull
    public String getAirshipComponentName() {
        return airshipComponentName;
    }

    @ConflictStrategy
    public int getConflictStrategy() {
        return conflictStrategy;
    }

    public long getMinInitialBackOffMs() {
        return minInitialBackOffMs;
    }

    @Override
    public String toString() {
        return "JobInfo{" +
                "action='" + action + '\'' +
                ", airshipComponentName='" + airshipComponentName + '\'' +
                ", isNetworkAccessRequired=" + isNetworkAccessRequired +
                ", initialDelay=" + initialDelay +
                ", conflictStrategy=" + conflictStrategy +
                ", minInitialBackOffMs=" + minInitialBackOffMs +
                ", extras=" + extras +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobInfo jobInfo = (JobInfo) o;
        return isNetworkAccessRequired == jobInfo.isNetworkAccessRequired &&
                initialDelay == jobInfo.initialDelay &&
                conflictStrategy == jobInfo.conflictStrategy &&
                minInitialBackOffMs == jobInfo.minInitialBackOffMs &&
                ObjectsCompat.equals(extras, jobInfo.extras) &&
                ObjectsCompat.equals(action, jobInfo.action) &&
                ObjectsCompat.equals(airshipComponentName, jobInfo.airshipComponentName);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(extras, action, airshipComponentName, isNetworkAccessRequired, initialDelay, conflictStrategy, minInitialBackOffMs);
    }

    /**
     * Creates a new job builder.
     *
     * @return A job builder.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * JobInfo builder.
     */
    public static class Builder {
        private final long MIN_INITIAL_BACKOFF_MS = 30000;
        private String action;
        private String airshipComponentName;
        private boolean isNetworkAccessRequired;
        private long initialDelay;
        private JsonMap extras;
        private int conflictStrategy = REPLACE;
        private long initialBackOffMs = MIN_INITIAL_BACKOFF_MS;

        private Builder() {
        }

        /**
         * The job's action.
         *
         * @param action The job's action.
         * @return The job builder.
         */
        @NonNull
        public Builder setAction(@Nullable String action) {
            this.action = action;
            return this;
        }

        @NonNull
        public Builder setInitialBackOff(long duration, @NonNull TimeUnit unit) {
            this.initialBackOffMs = Math.max(MIN_INITIAL_BACKOFF_MS, unit.toMillis(duration));
            return this;
        }

        /**
         * Sets if network access is required for the job.
         *
         * @param isNetworkAccessRequired Flag if network access is required.
         * @return The job builder.
         */
        @NonNull
        public Builder setNetworkAccessRequired(boolean isNetworkAccessRequired) {
            this.isNetworkAccessRequired = isNetworkAccessRequired;
            return this;
        }

        /**
         * Sets the {@link AirshipComponent} that will receive the job.
         *
         * @param component The airship component.
         * @return The job builder.
         */
        @NonNull
        public Builder setAirshipComponent(@NonNull Class<? extends AirshipComponent> component) {
            this.airshipComponentName = component.getName();
            return this;
        }

        /**
         * Sets initial delay.
         *
         * @param delay The initial delay.
         * @param unit The delay time unit.
         * @return The job builder.
         */
        @NonNull
        public Builder setInitialDelay(long delay, @NonNull TimeUnit unit) {
            this.initialDelay = unit.toMillis(delay);
            return this;
        }

        /**
         * Sets the {@link AirshipComponent} that will receive the job.
         *
         * @param componentName The airship component name.
         * @return The job builder.
         */
        @NonNull
        Builder setAirshipComponent(@Nullable String componentName) {
            this.airshipComponentName = componentName;
            return this;
        }

        /**
         * Sets the extras for the job.
         *
         * @param extras Bundle of extras.
         * @return The job builder.
         */
        @NonNull
        public Builder setExtras(@NonNull JsonMap extras) {
            this.extras = extras;
            return this;
        }


        @NonNull
        public Builder setConflictStrategy(@ConflictStrategy int conflictStrategy) {
            this.conflictStrategy = conflictStrategy;
            return this;
        }


        /**
         * Builds the job.
         *
         * @return The job.
         */
        @NonNull
        public JobInfo build() {
            Checks.checkNotNull(action, "Missing action.");
            return new JobInfo(this);
        }

    }

}
