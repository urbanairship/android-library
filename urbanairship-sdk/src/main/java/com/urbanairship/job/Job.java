/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.AirshipComponent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * Urban Airship job for defining a unit of work to be performed in an {@link AirshipComponent}.
 *
 * @hide
 */
public class Job {

    private static final String EXTRA_AIRSHIP_COMPONENT = "EXTRA_AIRSHIP_COMPONENT";
    private static final String EXTRA_JOB_EXTRAS = "EXTRA_JOB_EXTRAS";
    private static final String EXTRA_INITIAL_DELAY = "EXTRA_INITIAL_DELAY";
    private static final String EXTRA_JOB_ACTION = "EXTRA_JOB_ACTION";
    private static final String EXTRA_IS_NETWORK_ACCESS_REQUIRED = "EXTRA_IS_NETWORK_ACCESS_REQUIRED";
    private static final String EXTRA_JOB_TAG = "EXTRA_JOB_TAG";
    private static final String EXTRA_SCHEDULER_EXTRAS = "EXTRA_SCHEDULER_EXTRAS";

    @IntDef({ JOB_FINISHED, JOB_RETRY })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JobResult {}

    @Override
    public String toString() {
        return "Job{" +
                "action='" + action + '\'' +
                ", airshipComponentName='" + airshipComponentName + '\'' +
                ", tag='" + tag + '\'' +
                ", isNetworkAccessRequired=" + isNetworkAccessRequired +
                ", initialDelay=" + initialDelay +
                '}';
    }

    /**
     * Job is finished.
     */
    public static final int JOB_FINISHED = 0;

    /**
     * Job needs to be retried at a later date.
     */
    public static final int JOB_RETRY = 1;

    private final Bundle extras;
    private final String action;
    private final String airshipComponentName;
    private final String tag;
    private final boolean isNetworkAccessRequired;
    private final long initialDelay;
    private final Bundle schedulerExtras;

    private Job(@NonNull Builder builder) {
        this.action = builder.action == null ? "" : builder.action;
        this.airshipComponentName = builder.airshipComponentName;
        this.extras = builder.extras == null ? new Bundle() : new Bundle(builder.extras);
        this.tag = builder.tag == null ? UUID.randomUUID().toString() : builder.tag;
        this.isNetworkAccessRequired = builder.isNetworkAccessRequired;
        this.initialDelay = builder.initialDelay;
        this.schedulerExtras = builder.schedulerExtras == null ? new Bundle() : new Bundle(builder.schedulerExtras);
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
     * The job's tag.
     *
     * @return The job's tag.
     */
    @Nullable
    public String getTag() {
        return tag;
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
    public Bundle getExtras() {
        return extras;
    }

    /**
     * The job's scheduler extras..
     *
     * @return The job's scheduler extras.
     */
    Bundle getSchedulerExtras() {
        return schedulerExtras;
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

    /**
     * Creates a bundle containing the job info.
     *
     * @return A bundle representing the job.
     */
    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_AIRSHIP_COMPONENT, airshipComponentName);
        bundle.putString(EXTRA_JOB_ACTION, action);
        bundle.putBundle(EXTRA_JOB_EXTRAS, extras);
        bundle.putBundle(EXTRA_SCHEDULER_EXTRAS, schedulerExtras);
        bundle.putString(EXTRA_JOB_TAG, tag);
        bundle.putBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED, isNetworkAccessRequired);
        bundle.putLong(EXTRA_INITIAL_DELAY, initialDelay);
        return bundle;
    }

    /**
     * Creates a new job from a job bundle.
     *
     * @param bundle The job bundle.
     * @return Job builder.
     */
    public static Job fromBundle(Bundle bundle) {
        if (bundle == null) {
            return new Builder().build();
        }

        return new Builder()
                .setAction(bundle.getString(EXTRA_JOB_ACTION))
                .setTag(bundle.getString(EXTRA_JOB_TAG))
                .setInitialDelay(bundle.getLong(EXTRA_INITIAL_DELAY, 0), TimeUnit.MILLISECONDS)
                .setExtras(bundle.getBundle(EXTRA_JOB_EXTRAS))
                .setAirshipComponent(bundle.getString(EXTRA_AIRSHIP_COMPONENT))
                .setSchedulerExtras(bundle.getBundle(EXTRA_SCHEDULER_EXTRAS))
                .setNetworkAccessRequired(bundle.getBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED))
                .build();
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
     * Job builder.
     */
    public static class Builder {

        private Bundle extras;
        private String action;
        private String airshipComponentName;
        private String tag;

        private boolean isNetworkAccessRequired;
        private long initialDelay;
        public Bundle schedulerExtras;

        private Builder() {
        }

        /**
         * The job's action.
         *
         * @param action The job's action.
         * @return The job builder.
         */
        public Builder setAction(String action) {
            this.action = action;
            return this;
        }


        /**
         * Sets the job's tag. Tags are used to cancel and prevent multiple jobs fo the same type from
         * being scheduled.
         *
         * @param tag The job's tag.
         * @return The job builder.
         */
        public Builder setTag(String tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Sets if network access is required for the job.
         *
         * @param isNetworkAccessRequired Flag if network access is required.
         * @return The job builder.
         */
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
        Builder setAirshipComponent(String componentName) {
            this.airshipComponentName = componentName;
            return this;
        }

        /**
         * Sets the scheduler extras for the job.
         *
         * @param extras Bundle of extras.
         * @return The job builder.
         */
        Builder setSchedulerExtras(Bundle extras) {
            this.schedulerExtras = extras;
            return this;
        }

        /**
         * Sets the extras for the job.
         *
         * @param extras Bundle of extras.
         * @return The job builder.
         */
        public Builder setExtras(Bundle extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Puts an extra in the job's bundle.
         *
         * @param key The extra's key.
         * @param value The extra's value.
         * @return The job builder.
         */
        public Builder putExtra(String key, String value) {
            if (this.extras == null) {
                this.extras = new Bundle();
            }

            this.extras.putString(key, value);
            return this;
        }

        /**
         * Puts an extra in the job's bundle.
         *
         * @param key The extra's key.
         * @param value The extra's value.
         * @return The job builder.
         */
        public Builder putExtra(String key, int value) {
            if (this.extras == null) {
                this.extras = new Bundle();
            }

            this.extras.putInt(key, value);
            return this;
        }

        /**
         * Puts an extra in the job's bundle.
         *
         * @param key The extra's key.
         * @param value The extra's value.
         * @return The job builder.
         */
        public Builder putExtra(String key, Bundle value) {
            if (this.extras == null) {
                this.extras = new Bundle();
            }

            this.extras.putBundle(key, value);
            return this;
        }

        /**
         * Puts an extra in the job's bundle.
         *
         * @param key The extra's key.
         * @param value The extra's value.
         * @return The job builder.
         */
        public Builder putExtra(String key, Parcelable value) {
            if (this.extras == null) {
                this.extras = new Bundle();
            }

            this.extras.putParcelable(key, value);
            return this;
        }

        /**
         * Puts an extra in the job's bundle.
         *
         * @param key The extra's key.
         * @param value The extra's value.
         * @return The job builder.
         */
        public Builder putExtra(String key, Boolean value) {
            if (this.extras == null) {
                this.extras = new Bundle();
            }

            this.extras.putBoolean(key, value);
            return this;
        }

        /**
         * Builds the job.
         *
         * @return The job.
         */
        public Job build() {
            return new Job(this);
        }
    }
}
