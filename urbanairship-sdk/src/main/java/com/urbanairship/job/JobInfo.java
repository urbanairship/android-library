/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.job;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import com.urbanairship.AirshipComponent;
import com.urbanairship.Logger;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonValue;
import com.urbanairship.util.Checks;

import java.lang.annotation.Retention;
import java.util.concurrent.TimeUnit;

import static java.lang.annotation.RetentionPolicy.SOURCE;


/**
 * Urban Airship job for defining a unit of work to be performed in an {@link AirshipComponent}.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class JobInfo {


    @IntDef({ ANALYTICS_EVENT_UPLOAD, ANALYTICS_UPDATE_ADVERTISING_ID, NAMED_USER_UPDATE_ID,
              NAMED_USER_UPDATE_TAG_GROUPS, CHANNEL_UPDATE_PUSH_TOKEN, CHANNEL_UPDATE_REGISTRATION,
              CHANNEL_UPDATE_TAG_GROUPS, RICH_PUSH_UPDATE_USER, RICH_PUSH_UPDATE_MESSAGES,
              RICH_PUSH_SYNC_MESSAGE_STATE })
    @Retention(SOURCE)
    public @interface JobId {}

    public static final int ANALYTICS_EVENT_UPLOAD = 0;
    public static final int ANALYTICS_UPDATE_ADVERTISING_ID = 1;

    public static final int NAMED_USER_UPDATE_ID = 2;
    public static final int NAMED_USER_UPDATE_TAG_GROUPS = 3;

    public static final int CHANNEL_UPDATE_PUSH_TOKEN = 4;
    public static final int CHANNEL_UPDATE_REGISTRATION = 5;
    public static final int CHANNEL_UPDATE_TAG_GROUPS = 6;

    public static final int RICH_PUSH_UPDATE_USER = 7;
    public static final int RICH_PUSH_UPDATE_MESSAGES = 8;
    public static final int RICH_PUSH_SYNC_MESSAGE_STATE = 9;

    private static final String EXTRA_AIRSHIP_COMPONENT = "EXTRA_AIRSHIP_COMPONENT";
    private static final String EXTRA_JOB_EXTRAS = "EXTRA_JOB_EXTRAS";
    private static final String EXTRA_INITIAL_DELAY = "EXTRA_INITIAL_DELAY";
    private static final String EXTRA_JOB_ACTION = "EXTRA_JOB_ACTION";
    private static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    private static final String EXTRA_IS_NETWORK_ACCESS_REQUIRED = "EXTRA_IS_NETWORK_ACCESS_REQUIRED";
    private static final String EXTRA_PERSISTENT = "EXTRA_PERSISTENT";

    // ID generation
    private static final String SHARED_PREFERENCES_FILE = "com.urbanairship.job.ids";
    private static final String NEXT_GENERATED_ID_KEY = "next_generated_id";
    private static final int GENERATED_RANGE = 50;
    private static final int GENERATED_ID_OFFSET = 49;

    private static SharedPreferences sharedPreferences;
    private static final Object preferenceLock = new Object();

    private final JsonMap extras;
    private final String action;
    private final String airshipComponentName;
    private final boolean isNetworkAccessRequired;
    private final long initialDelay;
    private final boolean persistent;
    private final int id;


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

    /**
     * Default constructor.
     *
     * @param builder A builder instance.
     */
    private JobInfo(@NonNull Builder builder) {
        this.action = builder.action == null ? "" : builder.action;
        this.airshipComponentName = builder.airshipComponentName;
        this.extras = builder.extras != null ? builder.extras : JsonMap.EMPTY_MAP;
        this.isNetworkAccessRequired = builder.isNetworkAccessRequired;
        this.initialDelay = builder.initialDelay;
        this.persistent = builder.persistent;
        this.id = builder.jobId;
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
     * The job's ID.
     *
     * @return The job's ID.
     */
    public int getId() {
        return id;
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

    /**
     * If the Job should persists across reboots or not.
     *
     * @return {@code true} to persist across reboots, otherwise {@code false}.
     */
    public boolean isPersistent() {
        return persistent;
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
        bundle.putInt(EXTRA_JOB_ID, id);
        bundle.putString(EXTRA_JOB_EXTRAS, extras.toString());
        bundle.putBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED, isNetworkAccessRequired);
        bundle.putLong(EXTRA_INITIAL_DELAY, initialDelay);
        bundle.putBoolean(EXTRA_PERSISTENT, persistent);
        return bundle;
    }

    /**
     * Creates a persistable bundle containing the job info.
     *
     * @return A persistable bundle representing the job.
     */
    public PersistableBundle toPersistableBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(EXTRA_AIRSHIP_COMPONENT, airshipComponentName);
        bundle.putString(EXTRA_JOB_ACTION, action);
        bundle.putInt(EXTRA_JOB_ID, id);
        bundle.putString(EXTRA_JOB_EXTRAS, extras.toString());
        bundle.putBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED, isNetworkAccessRequired);
        bundle.putLong(EXTRA_INITIAL_DELAY, initialDelay);
        bundle.putBoolean(EXTRA_PERSISTENT, persistent);
        return bundle;
    }

    /**
     * Creates a jobInfo from a bundle.
     *
     * @param bundle The job bundle.
     * @return JobInfo builder.
     */
    @Nullable
    public static JobInfo fromBundle(Bundle bundle) {
        if (bundle == null) {
            return new Builder().build();
        }

        try {
            JobInfo.Builder builder = new Builder()
                    .setAction(bundle.getString(EXTRA_JOB_ACTION))
                    .setInitialDelay(bundle.getLong(EXTRA_INITIAL_DELAY, 0), TimeUnit.MILLISECONDS)
                    .setExtras(JsonValue.parseString(bundle.getString(EXTRA_JOB_EXTRAS)).optMap())
                    .setAirshipComponent(bundle.getString(EXTRA_AIRSHIP_COMPONENT))
                    .setNetworkAccessRequired(bundle.getBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED))
                    .setPersistent(bundle.getBoolean(EXTRA_PERSISTENT));

            //noinspection WrongConstant
            builder.setId(bundle.getInt(EXTRA_JOB_ID, 0));

            return builder.build();

        } catch (IllegalArgumentException | JsonException e) {
            Logger.error("Failed to parse job from bundle.", e);
        }

        return null;
    }

    /**
     * Creates a jobInfo from a persistable bundle.
     *
     * @param persistableBundle The job bundle.
     * @return JobInfo builder.
     */
    @Nullable
    static JobInfo fromPersistableBundle(PersistableBundle persistableBundle) {
        if (persistableBundle == null) {
            return new Builder().build();
        }

        try {
            JobInfo.Builder builder = new Builder()
                    .setAction(persistableBundle.getString(EXTRA_JOB_ACTION))
                    .setInitialDelay(persistableBundle.getLong(EXTRA_INITIAL_DELAY, 0), TimeUnit.MILLISECONDS)
                    .setExtras(JsonValue.parseString(persistableBundle.getString(EXTRA_JOB_EXTRAS)).optMap())
                    .setAirshipComponent(persistableBundle.getString(EXTRA_AIRSHIP_COMPONENT))
                    .setNetworkAccessRequired(persistableBundle.getBoolean(EXTRA_IS_NETWORK_ACCESS_REQUIRED))
                    .setPersistent(persistableBundle.getBoolean(EXTRA_PERSISTENT));

            //noinspection WrongConstant
            builder.setId(persistableBundle.getInt(EXTRA_JOB_ID, 0));

            return builder.build();
        } catch (IllegalArgumentException | JsonException e) {
            Logger.error("Failed to parse job from bundle.", e);
        }

        return null;
    }


    @Override
    public String toString() {
        return "JobInfo{" +
                "action=" + action +
                ", id=" + id +
                ", extras='" + extras + '\'' +
                ", airshipComponentName='" + airshipComponentName + '\'' +
                ", isNetworkAccessRequired=" + isNetworkAccessRequired +
                ", initialDelay=" + initialDelay +
                ", persistent=" + persistent +
                '}';
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

        private String action;
        private String airshipComponentName;

        private boolean isNetworkAccessRequired;
        private long initialDelay;
        private boolean persistent;
        private JsonMap extras;
        private int jobId = -1;

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
         * Sets the job's ID.
         *
         * @param id The job's ID.
         * @return The job builder.
         */
        public Builder setId(@JobId int id) {
            this.jobId = id;
            return this;
        }

        /**
         * Generates a unique ID for the job.
         *
         * @param context The application context.
         * @return The job builder.
         */
        @WorkerThread
        public Builder generateUniqueId(Context context) {
            synchronized (preferenceLock) {
                if (sharedPreferences == null) {
                    sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_FILE, Context.MODE_PRIVATE);
                }

                int id = sharedPreferences.getInt(NEXT_GENERATED_ID_KEY, 0);

                sharedPreferences.edit()
                                 .putInt(NEXT_GENERATED_ID_KEY, (id + 1) % GENERATED_RANGE)
                                 .apply();

                this.jobId = id + GENERATED_ID_OFFSET;
            }
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
         * Set whether or not to persist this job across device reboots.
         *
         * @param persistent {@code true} If the job should persist across reboots.
         * @return The job builder.
         */
        public Builder setPersistent(boolean persistent) {
            this.persistent = persistent;
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
         * Sets the extras for the job.
         *
         * @param extras Bundle of extras.
         * @return The job builder.
         */
        public Builder setExtras(JsonMap extras) {
            this.extras = extras;
            return this;
        }

        /**
         * Builds the job.
         *
         * @return The job.
         */
        public JobInfo build() {
            Checks.checkNotNull(action, "Missing action.");
            return new JobInfo(this);
        }
    }
}
