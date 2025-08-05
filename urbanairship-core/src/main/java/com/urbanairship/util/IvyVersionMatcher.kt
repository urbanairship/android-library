package com.urbanairship.util

import com.urbanairship.Predicate
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import java.util.Locale
import java.util.Objects
import java.util.regex.Pattern
import kotlin.jvm.Throws

/* Copyright Airship and Contributors */

/**
 * Ivy version matcher.
 *
 * @hide
 */
internal class IvyVersionMatcher private constructor(
    private val predicate: Predicate<String>,
    private val constraint: String
) : Predicate<String>, JsonSerializable {

    override fun apply(value: String): Boolean {
        val normalized = normalizeVersion(value) ?: return false
        return predicate.apply(normalized)
    }

    override fun toJsonValue(): JsonValue = JsonValue.wrap(constraint)

    /**
     * Helper class to compare version strings.
     */
    private class Version(version: String) : Comparable<Version> {

        val versionComponent: IntArray = intArrayOf(0, 0, 0)
        val version: String? = normalizeVersion(version)

        init {
            val components = this.version
                ?.split("\\.".toRegex())
                ?.dropLastWhile { it.isEmpty() }
                ?.toTypedArray()
                ?: emptyArray()

            for (i in 0..2) {
                if (components.size <= i) {
                    break
                }
                versionComponent[i] = components[i].toInt()
            }
        }

        override fun compareTo(other: Version): Int {
            for (i in 0..2) {
                val result = versionComponent[i] - other.versionComponent[i]
                if (result != 0) {
                    return if (result > 0) 1 else -1
                }
            }

            return 0
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }

        val that = other as IvyVersionMatcher

        return constraint == that.constraint
    }

    override fun hashCode(): Int {
        return Objects.hashCode(constraint)
    }

    public companion object {

        private const val START_INCLUSIVE = "["
        private const val START_EXCLUSIVE = "]"
        private const val START_INFINITE = "("
        private const val END_INCLUSIVE = "]"
        private const val END_EXCLUSIVE = "["
        private const val END_INFINITE = ")"
        private const val RANGE_SEPARATOR = ","
        private const val WHITESPACE = "\\s"

        private val START_PATTERN = String.format(
            Locale.US, "([\\%s\\%s\\%s])", START_INCLUSIVE, START_EXCLUSIVE, START_INFINITE
        )
        private val END_PATTERN =
            String.format(Locale.US, "([\\%s\\%s\\%s])", END_INCLUSIVE, END_EXCLUSIVE, END_INFINITE)

        private const val VERSION_REGEX = "([0-9]+)(\\.([0-9]+)((\\.([0-9]+))?(.*)))?"
        private val VERSION_PATTERN: Pattern = Pattern.compile(
            VERSION_REGEX
        )

        // Matches an optional version number, and optional (ignored) qualifier.
        private val VERSION_RANGE_PATTERN = String.format(
            Locale.US, "^%s(.*)?%s(.*)?%s$", START_PATTERN, RANGE_SEPARATOR, END_PATTERN
        )
        private const val SUB_VERSION_PATTERN = "^(.*)\\+$"
        private val EXACT_VERSION_PATTERN = "^$VERSION_PATTERN$"

        private val VERSION_RANGE: Pattern = Pattern.compile(
            VERSION_RANGE_PATTERN
        )
        private val EXACT_VERSION: Pattern = Pattern.compile(
            EXACT_VERSION_PATTERN
        )
        private val SUB_VERSION: Pattern = Pattern.compile(
            SUB_VERSION_PATTERN
        )

        /**
         * Factory method to create a version matcher.
         *
         * @param constraint The constraint.
         * @return An ivy version matcher.
         * @throws IllegalArgumentException If the constraint is invalid.
         */
        @Throws(IllegalArgumentException::class)
        public fun newMatcher(constraint: String): IvyVersionMatcher {
            var constraint = constraint
            constraint = constraint.replace(WHITESPACE.toRegex(), "")

            var predicate = parseSubVersionConstraint(constraint)
            if (predicate != null) {
                return IvyVersionMatcher(predicate, constraint)
            }

            predicate = parseExactVersionConstraint(constraint)
            if (predicate != null) {
                return IvyVersionMatcher(predicate, constraint)
            }

            predicate = parseVersionRangeConstraint(constraint)
            if (predicate != null) {
                return IvyVersionMatcher(predicate, constraint)
            }

            throw IllegalArgumentException("Invalid constraint: $constraint")
        }

        /**
         * Helper method to parse a subversion constraint.
         *
         * @param constraint The constraint string.
         * @return A predicate or null if the constraint is not a valid subversion.
         */
        private fun parseSubVersionConstraint(constraint: String): Predicate<String>? {
            val matcher = SUB_VERSION.matcher(constraint)
            if (!matcher.matches()) {
                return null
            }

            // +
            if ("+" == constraint) {
                return Predicate { _ -> true }
            }

            val version = if (matcher.groupCount() >= 1) matcher.group(1) else null
            val number = normalizeVersion(version)
            return Predicate { otherVersion ->
                if (number == null) {
                    return@Predicate false
                }
                otherVersion.startsWith(number)
            }
        }

        /** Trims whitespace and removes version qualifiers, to prepare a version for comparison.  */
        internal fun normalizeVersion(version: String?): String? {
            if (version == null) {
                return null
            }

            val trimmed = version.trim { it <= ' ' }
            val index = trimmed.indexOf('-')
            if (index < 0) {
                return trimmed
            }

            // Drop the qualifier, and add back a trailing plus, if we had one.
            return trimmed.substring(0, index) + (if (trimmed.endsWith("+")) "+" else "")
        }

        /**
         * Helper method to parse a version range constraint.
         *
         * @param constraint The constraint string.
         * @return A predicate or null if the constraint is not a valid version range.
         */
        private fun parseVersionRangeConstraint(constraint: String): Predicate<String>? {
            val matcher = VERSION_RANGE.matcher(constraint)
            if (!matcher.matches() || matcher.groupCount() != 4) {
                return null
            }

            val startToken = matcher.group(1)
            val startVersionString = normalizeVersion(matcher.group(2))
            val endVersionString = normalizeVersion(matcher.group(3))
            val endToken = matcher.group(4)

            // The VERSION_RANGE does not actually validate the versions to avoid a mass amount of groups
            val patternMatch = { input: String -> VERSION_PATTERN.matcher(input).matches() }
            if (!startVersionString.isNullOrEmpty() && !patternMatch(startVersionString)) {
                return null
            }
            if (!endVersionString.isNullOrEmpty() && !patternMatch(endVersionString)) {
                return null
            }

            val startVersion = if (startVersionString.isNullOrEmpty()) {
                null
            } else {
                Version(startVersionString)
            }

            val endVersion = if (endVersionString.isNullOrEmpty()) {
                null
            } else {
                Version(endVersionString)
            }

            if (END_INFINITE == endToken && endVersion != null) {
                return null
            }

            if (START_INFINITE == startToken && startVersion != null) {
                return null
            }

            return Predicate { otherVersion ->
                val version: Version
                try {
                    version = Version(otherVersion)
                } catch (e: NumberFormatException) {
                    return@Predicate false
                }

                if (endToken != null && endVersion != null) {
                    when (endToken) {
                        END_INCLUSIVE -> if (version > endVersion) {
                            return@Predicate false
                        }

                        END_EXCLUSIVE -> if (version >= endVersion) {
                            return@Predicate false
                        }
                    }
                }

                if (startToken != null && startVersion != null) {
                    when (startToken) {
                        START_INCLUSIVE -> if (version < startVersion) {
                            return@Predicate false
                        }

                        START_EXCLUSIVE -> if (version <= startVersion) {
                            return@Predicate false
                        }
                    }
                }
                true
            }
        }

        /**
         * Helper method to parse an exact version constraint.
         *
         * @param constraint The constraint string.
         * @return A predicate or null if the constraint is not a exact version constraint
         */
        private fun parseExactVersionConstraint(constraint: String): Predicate<String>? {
            val normalized = normalizeVersion(constraint) ?: return null

            if (!EXACT_VERSION.matcher(normalized).matches()) {
                return null
            }

            return Predicate { otherVersion -> normalized == normalizeVersion(otherVersion) }
        }
    }
}
