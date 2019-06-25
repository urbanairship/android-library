/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.analytics.CustomEvent;
import com.urbanairship.json.JsonMatcher;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.location.RegionEvent;
import com.urbanairship.util.UAStringUtil;
import com.urbanairship.util.VersionUtils;

/**
 * Class providing factory methods and builder classes for {@link Trigger}.
 */
public class Triggers {

    /**
     * Creates a new foreground trigger builder.
     *
     * @return The new foreground trigger builder.
     */
    @NonNull
    public static LifeCycleTriggerBuilder newForegroundTriggerBuilder() {
        return new LifeCycleTriggerBuilder(Trigger.LIFE_CYCLE_FOREGROUND);
    }

    /**
     * Creates a new background trigger builder.
     *
     * @return The new background trigger builder.
     */
    @NonNull
    public static LifeCycleTriggerBuilder newBackgroundTriggerBuilder() {
        return new LifeCycleTriggerBuilder(Trigger.LIFE_CYCLE_BACKGROUND);
    }

    /**
     * Creates a new app init trigger builder.
     *
     * @return The new app init trigger builder.
     */
    @NonNull
    public static LifeCycleTriggerBuilder newAppInitTriggerBuilder() {
        return new LifeCycleTriggerBuilder(Trigger.LIFE_CYCLE_APP_INIT);
    }

    /**
     * Creates a new enter region trigger builder.
     *
     * @return The new enter region trigger builder.
     */
    @NonNull
    public static RegionTriggerBuilder newEnterRegionTriggerBuilder() {
        return new RegionTriggerBuilder(Trigger.REGION_ENTER);
    }

    /**
     * Creates a new exit region trigger builder.
     *
     * @return The new exit region trigger builder.
     */
    @NonNull
    public static RegionTriggerBuilder newExitRegionTriggerBuilder() {
        return new RegionTriggerBuilder(Trigger.REGION_EXIT);
    }

    /**
     * Creates a new screen trigger builder.
     *
     * @return The new screen trigger builder.
     */
    @NonNull
    public static ScreenTriggerBuilder newScreenTriggerBuilder() {
        return new ScreenTriggerBuilder();
    }

    /**
     * Creates a new custom event trigger builder.
     *
     * @return The new custom event trigger builder.
     */
    @NonNull
    public static CustomEventTriggerBuilder newCustomEventTriggerBuilder() {
        return new CustomEventTriggerBuilder();
    }

    /**
     * Creates a new active session trigger builder.
     *
     * @return The new active session trigger builder.
     */
    @NonNull
    public static ActiveSessionTriggerBuilder newActiveSessionTriggerBuilder() {
        return new ActiveSessionTriggerBuilder();
    }

    /**
     * Create a new version trigger builder.
     *
     * @param versionMatcher The version matcher.
     * @return The new version trigger builder.
     */
    @NonNull
    public static VersionTriggerBuilder newVersionTriggerBuilder(@Nullable ValueMatcher versionMatcher) {
        return new VersionTriggerBuilder(versionMatcher);
    }

    /**
     * Lifecycle trigger Builder class.
     */
    public static class LifeCycleTriggerBuilder {

        private double goal = 1;
        private final int type;

        private LifeCycleTriggerBuilder(int type) {
            this.type = type;
        }

        /**
         * Sets the trigger goal.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public LifeCycleTriggerBuilder setGoal(double goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            return new Trigger(type, goal, null);
        }

    }

    /**
     * Region trigger Builder class.
     */
    public static class RegionTriggerBuilder {

        private final int type;
        private double goal = 1;
        private String regionId;

        private RegionTriggerBuilder(int type) {
            this.type = type;
        }

        /**
         * Sets the trigger goal.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public RegionTriggerBuilder setGoal(double goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Sets the region ID to trigger off of.
         *
         * @param regionId The region ID.
         * @return The Builder instance.
         */
        @NonNull
        public RegionTriggerBuilder setRegionId(@Nullable String regionId) {
            this.regionId = regionId;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            JsonPredicate predicate;
            if (UAStringUtil.isEmpty(regionId)) {
                predicate = null;
            } else {
                predicate = JsonPredicate.newBuilder()
                                         .addMatcher(JsonMatcher.newBuilder()
                                                                .setKey(RegionEvent.REGION_ID)
                                                                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(regionId)))
                                                                .build())
                                         .build();
            }

            return new Trigger(type, goal, predicate);
        }

    }

    /**
     * Screen trigger Builder class.
     */
    public static class ScreenTriggerBuilder {

        private double goal = 1;
        private String screenName;

        private ScreenTriggerBuilder() {
        }

        /**
         * Sets the trigger goal.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public ScreenTriggerBuilder setGoal(double goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Sets the screen name to trigger off of.
         *
         * @param screenName The screen name.
         * @return The Builder instance.
         */
        @NonNull
        public ScreenTriggerBuilder setScreenName(@Nullable String screenName) {
            this.screenName = screenName;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            JsonPredicate predicate;

            if (UAStringUtil.isEmpty(screenName)) {
                predicate = null;
            } else {
                predicate = JsonPredicate.newBuilder()
                                         .addMatcher(JsonMatcher.newBuilder()
                                                                .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(screenName)))
                                                                .build())
                                         .build();
            }

            return new Trigger(Trigger.SCREEN_VIEW, goal, predicate);
        }

    }

    /**
     * Custom event trigger Builder class.
     */
    public static class CustomEventTriggerBuilder {

        private double goal = 1;
        private int type;
        private String eventName;

        private CustomEventTriggerBuilder() {
        }

        /**
         * Sets the goal for {@link Trigger#CUSTOM_EVENT_COUNT} triggers.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public CustomEventTriggerBuilder setCountGoal(double goal) {
            this.type = Trigger.CUSTOM_EVENT_COUNT;
            this.goal = goal;
            return this;
        }

        /**
         * Sets the goal for {@link Trigger#CUSTOM_EVENT_VALUE} triggers.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public CustomEventTriggerBuilder setValueGoal(double goal) {
            this.type = Trigger.CUSTOM_EVENT_VALUE;
            this.goal = goal;
            return this;
        }

        /**
         * Sets the event name to trigger off of.
         *
         * @param eventName The event name.
         * @return The Builder instance.
         */
        @NonNull
        public CustomEventTriggerBuilder setEventName(@Nullable String eventName) {
            this.eventName = eventName;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            if (UAStringUtil.isEmpty(eventName)) {
                return new Trigger(type, goal, null);
            }

            JsonPredicate predicate = JsonPredicate.newBuilder()
                                                   .setPredicateType(JsonPredicate.AND_PREDICATE_TYPE)
                                                   .addMatcher(JsonMatcher.newBuilder()
                                                                          .setKey(CustomEvent.EVENT_NAME)
                                                                          .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(eventName)))
                                                                          .build())
                                                   .build();
            return new Trigger(type, goal, predicate);
        }

    }

    /**
     * Active session trigger builder class.
     */
    public static class ActiveSessionTriggerBuilder {

        private double goal = 1;

        private ActiveSessionTriggerBuilder() {
        }

        /**
         * Sets the goal for {@link Trigger#ACTIVE_SESSION} triggers.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public ActiveSessionTriggerBuilder setGoal(double goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            return new Trigger(Trigger.ACTIVE_SESSION, goal, null);
        }

    }

    /**
     * Version trigger builder class.
     */
    public static class VersionTriggerBuilder {

        private double goal = 1;
        private final ValueMatcher versionMatcher;

        private VersionTriggerBuilder(@Nullable ValueMatcher versionMatcher) {
            this.versionMatcher = versionMatcher;
        }

        /**
         * Sets the goal for {@link Trigger#VERSION} triggers.
         *
         * @param goal The trigger goal.
         * @return The Builder instance.
         */
        @NonNull
        public VersionTriggerBuilder setGoal(double goal) {
            this.goal = goal;
            return this;
        }

        /**
         * Builds the trigger instance.
         *
         * @return The trigger instance.
         */
        @NonNull
        public Trigger build() {
            JsonPredicate predicate = null;
            if (versionMatcher != null) {
                predicate = VersionUtils.createVersionPredicate(versionMatcher);
            }
            return new Trigger(Trigger.VERSION, goal, predicate);
        }

    }

}
