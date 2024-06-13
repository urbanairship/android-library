/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

/**
 * Thrown when a feature flag data is stale or outdated.
 */
public sealed class FeatureFlagException(override val message: String) : Exception(message) {
    public class FailedToFetch() : FeatureFlagException("failed to fetch data")
}

internal sealed class FeatureFlagEvaluationException(override val message: String) : Exception(message) {
    class ConnectionError() : FeatureFlagEvaluationException("Unable to fetch data")
    class OutOfDate() : FeatureFlagEvaluationException("Remote data is outdated")
    class StaleNotAllowed() : FeatureFlagEvaluationException("Stale data is not allowed")
}
