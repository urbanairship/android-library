package com.urbanairship.util;
/* Copyright Airship and Contributors */

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.urbanairship.Predicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ivy version matcher.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class IvyVersionMatcher implements Predicate<String>, JsonSerializable {

    private static final String START_INCLUSIVE = "[";
    private static final String START_EXCLUSIVE = "]";
    private static final String START_INFINITE = "(";
    private static final String END_INCLUSIVE = "]";
    private static final String END_EXCLUSIVE = "[";
    private static final String END_INFINITE = ")";
    private static final String RANGE_SEPARATOR = ",";
    private static final String WHITESPACE = "\\s";

    private static final String START_PATTERN = String.format(Locale.US, "([\\%s\\%s\\%s])", START_INCLUSIVE, START_EXCLUSIVE, START_INFINITE);
    private static final String END_PATTERN = String.format(Locale.US, "([\\%s\\%s\\%s])", END_INCLUSIVE, END_EXCLUSIVE, END_INFINITE);

    private static final String VERSION_PATTERN = "([0-9]+)(\\.[0-9]+)?(\\.[0-9]+)?";

    private static final String VERSION_RANGE_PATTERN = String.format(Locale.US, "^(%s(%s)?)%s((%s)?%s)", START_PATTERN, VERSION_PATTERN, RANGE_SEPARATOR, VERSION_PATTERN, END_PATTERN);
    private static final String SUB_VERSION_PATTERN = "^(.*)\\+$";
    private static final String EXACT_VERSION_PATTERN = "^" + VERSION_PATTERN + "$";

    private static final Pattern VERSION_RANGE = Pattern.compile(VERSION_RANGE_PATTERN);
    private static final Pattern EXACT_VERSION = Pattern.compile(EXACT_VERSION_PATTERN);
    private static final Pattern SUB_VERSION = Pattern.compile(SUB_VERSION_PATTERN);

    private final Predicate<String> predicate;
    private final String constraint;

    private IvyVersionMatcher(Predicate<String> predicate, String constraint) {
        this.predicate = predicate;
        this.constraint = constraint;
    }

    /**
     * Factory method to create a version matcher.
     *
     * @param constraint The constraint.
     * @return An ivy version matcher.
     * @throws IllegalArgumentException If the constraint is invalid.
     */
    @NonNull
    public static IvyVersionMatcher newMatcher(@NonNull String constraint) {
        constraint = constraint.replaceAll(WHITESPACE, "");

        Predicate<String> predicate = parseExactVersionConstraint(constraint);
        if (predicate != null) {
            return new IvyVersionMatcher(predicate, constraint);
        }

        predicate = parseSubVersionConstraint(constraint);
        if (predicate != null) {
            return new IvyVersionMatcher(predicate, constraint);
        }

        predicate = parseVersionRangeConstraint(constraint);
        if (predicate != null) {
            return new IvyVersionMatcher(predicate, constraint);
        }

        throw new IllegalArgumentException("Invalid constraint: " + constraint);
    }

    @Override
    public boolean apply(@Nullable String versionString) {
        if (versionString == null) {
            return false;
        }
        return predicate.apply(versionString.trim());
    }

    /**
     * Helper method to parse a subversion constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a valid subversion.
     */
    @Nullable
    private static Predicate<String> parseSubVersionConstraint(String constraint) {
        Matcher matcher = SUB_VERSION.matcher(constraint);
        if (!matcher.matches()) {
            return null;
        }

        // +
        if ("+".equals(constraint)) {
            return new Predicate<String>() {
                @Override
                public boolean apply(String object) {
                    return true;
                }
            };
        }

        final String number = matcher.groupCount() >= 1 ? matcher.group(1) : null;
        return new Predicate<String>() {
            @Override
            public boolean apply(@NonNull String version) {
                if (number == null) {
                    return false;
                }

                return version.startsWith(number);
            }
        };
    }

    /**
     * Helper method to parse a version range constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a valid version range.
     */
    @Nullable
    private static Predicate<String> parseVersionRangeConstraint(String constraint) {
        Matcher matcher = VERSION_RANGE.matcher(constraint);
        if (!matcher.matches()) {
            return null;
        }

        final String endToken;
        final Version endVersion;
        final String startToken;
        final Version startVersion;

        String end = matcher.groupCount() >= 7 ? matcher.group(7) : null;
        if (!UAStringUtil.isEmpty(end)) {
            endToken = end.substring(end.length() - 1);
            endVersion = end.length() > 1 ? new Version(end.substring(0, end.length() - 1)) : null;
        } else {
            endToken = null;
            endVersion = null;
        }

        final String start = matcher.groupCount() >= 1 ? matcher.group(1) : null;
        if (!UAStringUtil.isEmpty(start)) {
            startToken = start.substring(0, 1);
            startVersion = start.length() > 1 ? new Version(start.substring(1)) : null;
        } else {
            startToken = null;
            startVersion = null;
        }

        if (END_INFINITE.equals(endToken) && endVersion != null) {
            return null;
        }

        if (START_INFINITE.equals(startToken) && startVersion != null) {
            return null;
        }

        return new Predicate<String>() {
            @Override
            public boolean apply(@NonNull String object) {

                Version version;
                try {
                    version = new Version(object);
                } catch (NumberFormatException e) {
                    return false;
                }

                if (endToken != null && endVersion != null) {
                    switch (endToken) {
                        case END_INCLUSIVE:
                            if (version.compareTo(endVersion) > 0) {
                                return false;
                            }
                            break;
                        case END_EXCLUSIVE:
                            if (version.compareTo(endVersion) >= 0) {
                                return false;
                            }
                            break;
                    }
                }

                if (startToken != null && startVersion != null) {
                    switch (startToken) {
                        case START_INCLUSIVE:
                            if (version.compareTo(startVersion) < 0) {
                                return false;
                            }
                            break;
                        case START_EXCLUSIVE:
                            if (version.compareTo(startVersion) <= 0) {
                                return false;
                            }
                            break;
                    }
                }

                return true;
            }
        };
    }

    /**
     * Helper method to parse an exact version constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a exact version constraint
     */
    @Nullable
    private static Predicate<String> parseExactVersionConstraint(@NonNull final String constraint) {
        if (!EXACT_VERSION.matcher(constraint).matches()) {
            return null;
        }

        return new Predicate<String>() {
            @Override
            public boolean apply(String object) {
                return constraint.equals(object);
            }
        };
    }

    @NonNull
    @Override
    public JsonValue toJsonValue() {
        return JsonValue.wrap(constraint);
    }

    /**
     * Helper class to compare version strings.
     */
    private static class Version implements Comparable<Version> {

        final int[] versionComponent = new int[] { 0, 0, 0 };
        final String version;

        public Version(String version) {
            this.version = version;

            String[] components = version.split("\\.");
            for (int i = 0; i < 3; i++) {
                if (components.length <= i) {
                    break;
                }
                versionComponent[i] = Integer.valueOf(components[i]);
            }
        }

        @Override
        public int compareTo(@NonNull Version version) {
            for (int i = 0; i < 3; i++) {
                int result = this.versionComponent[i] - version.versionComponent[i];
                if (result != 0) {
                    return result > 0 ? 1 : -1;
                }
            }

            return 0;
        }

    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IvyVersionMatcher that = (IvyVersionMatcher) o;

        return constraint != null ? constraint.equals(that.constraint) : that.constraint == null;
    }

    @Override
    public int hashCode() {
        return constraint != null ? constraint.hashCode() : 0;
    }

}
