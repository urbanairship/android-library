package com.urbanairship.featureflag

/**
 * Thrown when a feature flag data is stale or outdated.
 */
class FeatureFlagException(override val message: String) : Exception(message)
