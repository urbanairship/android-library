/* Copyright Airship and Contributors */

package com.urbanairship.automation;

import com.urbanairship.audience.AudienceSelector;
import com.urbanairship.audience.DeviceTagSelector;
import com.urbanairship.automation.tags.TagSelector;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.util.VersionUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;

/**
 * Audience conditions for an in-app message. Audiences are normally only validated at display time,
 * and if the audience is not met, the in-app message will not be displayed.
 */
public class Audience implements JsonSerializable {

    // Not used anymore internally, but still around to avoid breaking the public API.

    private AudienceSelector audienceSelector;

    @StringDef({ MISS_BEHAVIOR_CANCEL, MISS_BEHAVIOR_SKIP, MISS_BEHAVIOR_PENALIZE })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MissBehavior {}


    /**
     * Cancel the message's schedule when the audience check fails.
     */
    @NonNull
    public static final String MISS_BEHAVIOR_CANCEL = "cancel";

    /**
     * Skip the message's schedule when the audience check fails.
     */
    @NonNull
    public static final String MISS_BEHAVIOR_SKIP = "skip";

    /**
     * Skip and penalize the message's schedule when the audience check fails.
     */
    @NonNull
    public static final String MISS_BEHAVIOR_PENALIZE = "penalize";

    /**
     * Default constructor.
     *
     * @param selector The AudienceSelector object.
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public Audience(@NonNull AudienceSelector selector) {
        audienceSelector = selector;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return audienceSelector.toJsonValue();
    }

    /**
     * Parses the json value.
     *
     * @param value The json value.
     * @return The audience condition.
     * @throws JsonException If the json is invalid.
     */
    @NonNull
    public static Audience fromJson(@NonNull JsonValue value) throws JsonException {
        return new Audience(AudienceSelector.Companion.fromJson(value));
    }

    /**
     * Gets the list of language tags.
     *
     * @return A list of language tags.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getLanguageTags() {
        return audienceSelector.getLanguageTags();
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public AudienceSelector getAudienceSelector() {
        return audienceSelector;
    }

    /**
     * Gets the list of test devices.
     *
     * @return A list of test devices.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public List<String> getTestDevices() {
        return audienceSelector.getTestDevices();
    }

    /**
     * Gets the notification opt-in status.
     *
     * @return The notification opt-in status.
     */
    @Nullable
    public Boolean getNotificationsOptIn() {
        return audienceSelector.getNotificationsOptIn();
    }

    /**
     * Gets the location opt-in status.
     *
     * @return The location opt-in status.
     */
    @Nullable
    public Boolean getLocationOptIn() {
        return audienceSelector.getLocationOptIn();
    }

    /**
     * Gets the requires analytics flag.
     *
     * @return The requires analytics flag.
     */
    @Nullable
    public Boolean getRequiresAnalytics() {
        return audienceSelector.getRequiresAnalytics();
    }

    /**
     * Gets the new user status.
     *
     * @return The new user status.
     */
    @Nullable
    public Boolean getNewUser() {
        return audienceSelector.getNewUser();
    }

    /**
     * Gets the tag selector.
     *
     * @return The tag selector.
     */
    @Nullable
    public TagSelector getTagSelector() {
        DeviceTagSelector core = audienceSelector.getTagSelector();
        if (core == null) {
            return null;
        }

        return new TagSelector(core);
    }

    /**
     * Gets the app version predicate.
     *
     * @return The app version predicate.
     */
    @Nullable
    public JsonPredicate getVersionPredicate() {
        return audienceSelector.getVersionPredicate();
    }

    /**
     * Gets the permissions predicate.
     *
     * @return The permissions predicate.
     */
    @Nullable
    public JsonPredicate getPermissionsPredicate() {
        return audienceSelector.getPermissionsPredicate();
    }

    /**
     * Gets the audience miss behavior.
     *
     * @return The audience miss behavior.
     */
    @NonNull
    public String getMissBehavior() {
        return audienceSelector.getMissBehavior().getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Audience audience = (Audience) o;
        return audienceSelector.equals(audience.audienceSelector);
    }

    @Override
    public int hashCode() {
        return audienceSelector.hashCode();
    }

    @Override
    public String toString() {
        return audienceSelector.toString();
    }

    /**
     * Builder factory method.
     *
     * @return A new builder instance.
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Audience builder.
     */
    public static class Builder {

        private AudienceSelector.Builder coreBuilder;

        private Builder() {
            coreBuilder = AudienceSelector.Companion.newBuilder();
        }

        /**
         * Sets the new user audience condition for scheduling the in-app message.
         *
         * @param newUser {@code true} if only new users should schedule the in-app message, otherwise {@code false}.
         * @return The builder.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        Builder setNewUser(boolean newUser) {
            coreBuilder.setNewUser(newUser);
            return this;
        }

        /**
         * Adds a test device.
         *
         * @param hash The hashed channel.
         * @return THe builder.
         * @hide
         */
        @NonNull
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        Builder addTestDevice(String hash) {
            coreBuilder.addTestDevice(hash);
            return this;
        }

        /**
         * Sets the location opt-in audience condition for the in-app message.
         *
         * @param optIn {@code true} if location must be opted in, otherwise {@code false}.
         * @return The builder.
         * @hide
         */
        @NonNull
        public Builder setLocationOptIn(boolean optIn) {
            coreBuilder.setLocationOptIn(optIn);
            return this;
        }

        /**
         * Sets the require analytics audience condition for the in-app message.
         *
         * @param requiresAnalytics {@code true} if analytics must be enabled, otherwise {@code false}.
         * @return The builder.
         * @hide
         */
        @NonNull
        public Builder setRequiresAnalytics(boolean requiresAnalytics) {
            coreBuilder.setRequiresAnalytics(requiresAnalytics);
            return this;
        }

        /**
         * Sets the notification opt-in audience condition for the in-app message.
         *
         * @param optIn {@code true} if notifications must be opted in, otherwise {@code false}.
         * @return The builder.
         * @hide
         */
        @NonNull
        public Builder setNotificationsOptIn(boolean optIn) {
            coreBuilder.setNotificationsOptIn(optIn);
            return this;
        }

        /**
         * Adds a BCP 47 location tag. Only the language and country code are used
         * to determine the audience.
         *
         * @param languageTag A BCP 47 language tag.
         * @return The builder.
         */
        @NonNull
        public Builder addLanguageTag(@NonNull String languageTag) {
            coreBuilder.addLanguageTag(languageTag);
            return this;
        }

        /**
         * Value predicate to be used to match the app's version int.
         *
         * @param predicate Json predicate to match the version object.
         * @return The builder.
         */
        @NonNull
        private Builder setVersionPredicate(@Nullable JsonPredicate predicate) {
            coreBuilder.setVersionPredicate(predicate);
            return this;
        }

        /**
         * JSON predicate to be used to match the app's permissions map.
         *
         * @param predicate Json predicate to match the permissions map.
         * @return The builder.
         */
        @NonNull
        public Builder setPermissionsPredicate(@NonNull JsonPredicate predicate) {
            coreBuilder.setPermissionsPredicate(predicate);
            return this;
        }

        /**
         * Value matcher to be used to match the app's version int.
         *
         * @param valueMatcher Value matcher to be applied to the app's version int.
         * @return The builder.
         */
        @NonNull
        public Builder setVersionMatcher(@Nullable ValueMatcher valueMatcher) {
            return setVersionPredicate(valueMatcher == null ? null : VersionUtils.createVersionPredicate(valueMatcher));
        }

        /**
         * Sets the tag selector. Tag selector will only be applied to channel tags set through
         * the SDK.
         *
         * @param tagSelector The tag selector.
         * @return The builder.
         */
        @NonNull
        public Builder setTagSelector(@Nullable TagSelector tagSelector) {
            coreBuilder.setTagSelector(tagSelector.getTagSelector());
            return this;
        }

        /**
         * Sets the audience miss behavior for the in-app message.
         *
         * @param missBehavior The audience miss behavior.
         * @return The builder.
         * @hide
         */
        @NonNull
        public Builder setMissBehavior(@NonNull @MissBehavior String missBehavior) {
            AudienceSelector.MissBehavior behavior = AudienceSelector.MissBehavior.Companion.parse(missBehavior);
            coreBuilder.setMissBehavior(behavior);
            return this;
        }

        /**
         * Builds the in-app message audience.
         *
         * @return The audience.
         */
        @NonNull
        public Audience build() {
            return new Audience(coreBuilder.build());
        }
    }
}
