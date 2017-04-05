/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;

import com.urbanairship.AirshipComponent;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


/**
 * Urban Airship job for defining a unit of work to be performed in an {@link AirshipComponent}.
 */
public class Job {

    @IntDef({ JOB_FINISHED, JOB_RETRY })
    @Retention(RetentionPolicy.SOURCE)
    public @interface JobResult {}

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

    private Job(@NonNull Builder builder) {
        this.action = builder.action;
        this.airshipComponentName = builder.airshipComponentName;
        this.extras = builder.extras == null ? new Bundle() : new Bundle(builder.extras);
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
     * The job's extras.
     *
     * @return The job's extras.
     */
    @NonNull
    public Bundle getExtras() {
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

    /**
     * Creates a new job builder.
     *
     * @param action The job's action.
     * @return A job builder.
     */
    @NonNull
    public static Builder newBuilder(@NonNull String action) {
        return new Builder(action);
    }

    /**
     * Job builder.
     */
    public static class Builder {

        private Bundle extras;
        private String action;
        private String airshipComponentName;

        private Builder(@NonNull String action) {
            this.action = action;
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
