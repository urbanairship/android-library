/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.StringDef;

import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;
import com.urbanairship.json.JsonPredicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;
import com.urbanairship.json.ValueMatcher;
import com.urbanairship.util.VersionUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

/**
 * Audience conditions for an in-app message. Audiences are normally only validated at display time,
 * and if the audience is not met, the in-app message will not be displayed.
 */
public class Audience implements JsonSerializable {

    // JSON keys
    private static final String NEW_USER_KEY = "new_user";
    private static final String NOTIFICATION_OPT_IN_KEY = "notification_opt_in";
    private static final String LOCATION_OPT_IN_KEY = "location_opt_in";
    private static final String LOCALE_KEY = "locale";
    private static final String APP_VERSION_KEY = "app_version";
    private static final String TAGS_KEY = "tags";
    private static final String TEST_DEVICES_KEY = "test_devices";
    private static final String MISS_BEHAVIOR_KEY = "miss_behavior";

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

    private final Boolean newUser;
    private final Boolean notificationsOptIn;
    private final Boolean locationOptIn;
    private final List<String> languageTags;
    private final List<String> testDevices;
    private final TagSelector tagSelector;
    private final JsonPredicate versionPredicate;
    private final String missBehavior;

    /**
     * Default constructor.
     *
     * @param builder The builder.
     */
    private Audience(@NonNull Builder builder) {
        this.newUser = builder.newUser;
        this.notificationsOptIn = builder.notificationsOptIn;
        this.locationOptIn = builder.locationOptIn;
        this.languageTags = builder.languageTags;
        this.tagSelector = builder.tagSelector;
        this.versionPredicate = builder.versionPredicate;
        this.testDevices = builder.testDevices;
        this.missBehavior = builder.missBehavior;
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonMap.newBuilder()
                      .putOpt(NEW_USER_KEY, newUser)
                      .putOpt(NOTIFICATION_OPT_IN_KEY, notificationsOptIn)
                      .putOpt(LOCATION_OPT_IN_KEY, locationOptIn)
                      .put(LOCALE_KEY, languageTags.isEmpty() ? null : JsonValue.wrapOpt(languageTags))
                      .put(TEST_DEVICES_KEY, testDevices.isEmpty() ? null : JsonValue.wrapOpt(testDevices))
                      .put(TAGS_KEY, tagSelector)
                      .put(APP_VERSION_KEY, versionPredicate)
                      .put(MISS_BEHAVIOR_KEY, missBehavior)
                      .build().toJsonValue();
    }

    /**
     * {@see #fromJson(JsonValue)}
     *
     * @param json The json value.
     * @return The parsed Audience.
     * @throws JsonException If the JSON is invalid.
     * @deprecated To be removed in SDK 12. Use {@link #fromJson(JsonValue)} instead.
     */
    @NonNull
    @Deprecated
    public static Audience parseJson(@NonNull JsonValue json) throws JsonException {
        return fromJson(json);
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
        JsonMap content = value.optMap();

        Builder builder = newBuilder();

        // New User
        if (content.containsKey(NEW_USER_KEY)) {
            if (!content.opt(NEW_USER_KEY).isBoolean()) {
                throw new JsonException("new_user must be a boolean: " + content.get(NEW_USER_KEY));
            }
            builder.setNewUser(content.opt(NEW_USER_KEY).getBoolean(false));
        }

        // Push Opt-in
        if (content.containsKey(NOTIFICATION_OPT_IN_KEY)) {
            if (!content.opt(NOTIFICATION_OPT_IN_KEY).isBoolean()) {
                throw new JsonException("notification_opt_in must be a boolean: " + content.get(NOTIFICATION_OPT_IN_KEY));
            }
            builder.setNotificationsOptIn(content.opt(NOTIFICATION_OPT_IN_KEY).getBoolean(false));
        }

        // Location Opt-in
        if (content.containsKey(LOCATION_OPT_IN_KEY)) {
            if (!content.opt(LOCATION_OPT_IN_KEY).isBoolean()) {
                throw new JsonException("location_opt_in must be a boolean: " + content.get(LOCATION_OPT_IN_KEY));
            }
            builder.setLocationOptIn(content.opt(LOCATION_OPT_IN_KEY).getBoolean(false));
        }

        // Locale
        if (content.containsKey(LOCALE_KEY)) {
            if (!content.opt(LOCALE_KEY).isJsonList()) {
                throw new JsonException("locales must be an array: " + content.get(LOCALE_KEY));
            }

            for (JsonValue val : content.opt(LOCALE_KEY).optList()) {
                String tag = val.getString();
                if (tag == null) {
                    throw new JsonException("Invalid locale: " + val);
                }

                builder.addLanguageTag(tag);
            }
        }

        // App Version
        if (content.containsKey(APP_VERSION_KEY)) {
            builder.setVersionPredicate(JsonPredicate.parse(content.get(APP_VERSION_KEY)));
        }

        // Tags
        if (content.containsKey(TAGS_KEY)) {
            builder.setTagSelector(TagSelector.fromJson(content.opt(TAGS_KEY)));
        }

        // Test devices
        if (content.containsKey(TEST_DEVICES_KEY)) {
            if (!content.opt(TEST_DEVICES_KEY).isJsonList()) {
                throw new JsonException("test devices must be an array: " + content.get(LOCALE_KEY));
            }

            for (JsonValue val : content.opt(TEST_DEVICES_KEY).optList()) {
                if (!val.isString()) {
                    throw new JsonException("Invalid test device: " + val);
                }

                builder.addTestDevice(val.getString());
            }
        }

        // Miss Behavior
        if (content.containsKey(MISS_BEHAVIOR_KEY)) {
            if (!content.opt(MISS_BEHAVIOR_KEY).isString()) {
                throw new JsonException("miss_behavior must be a string: " + content.get(MISS_BEHAVIOR_KEY));
            }

            switch (content.opt(MISS_BEHAVIOR_KEY).optString()) {
                case MISS_BEHAVIOR_CANCEL:
                    builder.setMissBehavior(MISS_BEHAVIOR_CANCEL);
                    break;
                case MISS_BEHAVIOR_SKIP:
                    builder.setMissBehavior(MISS_BEHAVIOR_SKIP);
                    break;
                case MISS_BEHAVIOR_PENALIZE:
                    builder.setMissBehavior(MISS_BEHAVIOR_PENALIZE);
                    break;
                default:
                    throw new JsonException("Invalid miss behavior: " + content.opt(MISS_BEHAVIOR_KEY));
            }

        }

        return builder.build();
    }

    /**
     * Gets the list of language tags.
     *
     * @return A list of language tags.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    List<String> getLanguageTags() {
        return languageTags;
    }

    /**
     * Gets the list of test devices.
     *
     * @return A list of test devices.
     * @hide
     */
    @NonNull
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    List<String> getTestDevices() {
        return testDevices;
    }

    /**
     * Gets the notification opt-in status.
     *
     * @return The notification opt-in status.
     */
    @Nullable
    Boolean getNotificationsOptIn() {
        return notificationsOptIn;
    }

    /**
     * Gets the location opt-in status.
     *
     * @return The location opt-in status.
     */
    @Nullable
    Boolean getLocationOptIn() {
        return locationOptIn;
    }

    /**
     * Gets the new user status.
     *
     * @return The new user status.
     */
    @Nullable
    Boolean getNewUser() {
        return newUser;
    }

    /**
     * Gets the tag selector.
     *
     * @return The tag selector.
     */
    @Nullable
    TagSelector getTagSelector() {
        return tagSelector;
    }

    /**
     * Gets the app version predicate.
     *
     * @return The app version predicate.
     */
    @Nullable
    JsonPredicate getVersionPredicate() {
        return versionPredicate;
    }

    /**
     * Gets the audience miss behavior.
     *
     * @return The audience miss behavior.
     */
    @NonNull
    String getMissBehavior() {
        return missBehavior;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Audience audience = (Audience) o;

        if (newUser != null ? !newUser.equals(audience.newUser) : audience.newUser != null) {
            return false;
        }
        if (notificationsOptIn != null ? !notificationsOptIn.equals(audience.notificationsOptIn) : audience.notificationsOptIn != null) {
            return false;
        }
        if (locationOptIn != null ? !locationOptIn.equals(audience.locationOptIn) : audience.locationOptIn != null) {
            return false;
        }
        if (languageTags != null ? !languageTags.equals(audience.languageTags) : audience.languageTags != null) {
            return false;
        }
        if (tagSelector != null ? !tagSelector.equals(audience.tagSelector) : audience.tagSelector != null) {
            return false;
        }
        if (missBehavior != null ? !missBehavior.equals(audience.missBehavior) : audience.missBehavior != null) {
            return false;
        }
        return versionPredicate != null ? versionPredicate.equals(audience.versionPredicate) : audience.versionPredicate == null;
    }

    @Override
    public int hashCode() {
        int result = newUser != null ? newUser.hashCode() : 0;
        result = 31 * result + (notificationsOptIn != null ? notificationsOptIn.hashCode() : 0);
        result = 31 * result + (locationOptIn != null ? locationOptIn.hashCode() : 0);
        result = 31 * result + (languageTags != null ? languageTags.hashCode() : 0);
        result = 31 * result + (tagSelector != null ? tagSelector.hashCode() : 0);
        result = 31 * result + (versionPredicate != null ? versionPredicate.hashCode() : 0);
        result = 31 * result + (missBehavior != null ? missBehavior.hashCode() : 0);
        return result;
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

        private Boolean newUser;
        private Boolean notificationsOptIn;
        private Boolean locationOptIn;
        private final List<String> languageTags = new ArrayList<>();
        private final List<String> testDevices = new ArrayList<>();
        private String missBehavior = MISS_BEHAVIOR_PENALIZE;

        private TagSelector tagSelector;
        private JsonPredicate versionPredicate;

        private Builder() {
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
            this.newUser = newUser;
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
            this.testDevices.add(hash);
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
            this.locationOptIn = optIn;
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
            this.notificationsOptIn = optIn;
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
            languageTags.add(languageTag);
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
            this.versionPredicate = predicate;
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
            this.tagSelector = tagSelector;
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
            this.missBehavior = missBehavior;
            return this;
        }

        /**
         * Builds the in-app message audience.
         *
         * @return The audience.
         */
        @NonNull
        public Audience build() {
            return new Audience(this);
        }

    }

}
