package com.urbanairship.util;
/* Copyright Airship and Contributors */

import com.urbanairship.Predicate;
import com.urbanairship.json.JsonSerializable;
import com.urbanairship.json.JsonValue;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

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

    private static final String VERSION_REGEX = "([0-9]+)(\\.([0-9]+)((\\.([0-9]+))?(.*)))?";
    private static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEX);

    // Matches an optional version number, and optional (ignored) qualifier.
    private static final String VERSION_RANGE_PATTERN = String.format(Locale.US, "^%s(.*)?%s(.*)?%s$", START_PATTERN, RANGE_SEPARATOR, END_PATTERN);
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


        Predicate<String> predicate = parseSubVersionConstraint(constraint);
        if (predicate != null) {
            return new IvyVersionMatcher(predicate, constraint);
        }

         predicate = parseExactVersionConstraint(constraint);
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
        return predicate.apply(normalizeVersion(versionString));
    }

    /**
     * Helper method to parse a subversion constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a valid subversion.
     */
    @Nullable
    private static Predicate<String> parseSubVersionConstraint(@NonNull String constraint) {
        Matcher matcher = SUB_VERSION.matcher(constraint);
        if (!matcher.matches()) {
            return null;
        }

        // +
        if ("+".equals(constraint)) {
            return ignored -> true;
        }

        final String number = normalizeVersion(matcher.groupCount() >= 1 ? matcher.group(1) : null);
        return otherVersion -> {
            if (number == null) {
                return false;
            }
            return otherVersion.startsWith(number);
        };
    }

    /** Trims whitespace and removes version qualifiers, to prepare a version for comparison. */
    @VisibleForTesting
    static String normalizeVersion(@Nullable String version) {
        if (version == null) {
            return null;
        }

        String trimmed = version.trim();
        int index = trimmed.indexOf('-');
        return index > 0
                // Drop the qualifier, and add back a trailing plus, if we had one.
                ? trimmed.substring(0, index) + (trimmed.endsWith("+") ? "+" : "")
                : trimmed;
    }

    /**
     * Helper method to parse a version range constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a valid version range.
     */
    @Nullable
    private static Predicate<String> parseVersionRangeConstraint(@NonNull String constraint) {
        Matcher matcher = VERSION_RANGE.matcher(constraint);
        if (!matcher.matches() || matcher.groupCount() != 4) {
            return null;
        }

        final String startToken = matcher.group(1);
        final String startVersionString = normalizeVersion(matcher.group(2));
        final String endVersionString = normalizeVersion(matcher.group(3));
        final String endToken = matcher.group(4);

        // The VERSION_RANGE does not actually validate the versions to avoid a mass amount of groups
        if (!UAStringUtil.isEmpty(startVersionString) && !VERSION_PATTERN.matcher(startVersionString).matches()) {
            return null;
        }
        if (!UAStringUtil.isEmpty(endVersionString) && !VERSION_PATTERN.matcher(endVersionString).matches()) {
            return null;
        }

        final Version startVersion = UAStringUtil.isEmpty(startVersionString) ? null : new Version(startVersionString);
        final Version endVersion = UAStringUtil.isEmpty(endVersionString) ? null : new Version(endVersionString);

        if (END_INFINITE.equals(endToken) && endVersion != null) {
            return null;
        }

        if (START_INFINITE.equals(startToken) && startVersion != null) {
            return null;
        }

        return otherVersion -> {
            Version version;
            try {
                version = new Version(otherVersion);
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
        };
    }

    /**
     * Helper method to parse an exact version constraint.
     *
     * @param constraint The constraint string.
     * @return A predicate or null if the constraint is not a exact version constraint
     */
    @Nullable
    private static Predicate<String> parseExactVersionConstraint(@NonNull String constraint) {
        String normalized = normalizeVersion(constraint);
        if (!EXACT_VERSION.matcher(normalized).matches()) {
            return null;
        }

        return otherVersion -> normalized.equals(normalizeVersion(otherVersion));
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

        public Version(@NonNull String version) {
            this.version = normalizeVersion(version);

            String[] components = this.version.split("\\.");
            for (int i = 0; i < 3; i++) {
                if (components.length <= i) {
                    break;
                }
                versionComponent[i] = Integer.parseInt(components[i]);
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

        return Objects.equals(constraint, that.constraint);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(constraint);
    }

}
