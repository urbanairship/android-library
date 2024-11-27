/* Copyright Airship and Contributors */

package com.urbanairship.featureflag

/**
 * Thrown when a feature flag data is stale or outdated.
 */
public sealed class FeatureFlagException(override val message: String) : Exception(message) {
    public class FailedToFetch(message: String) : FeatureFlagException(message)
}

internal sealed class FeatureFlagEvaluationException(override val message: String) : Exception(message) {
    class ConnectionError(
        val statusCode: Int? = null,
        val errorDescription: String? = null,
    ) : FeatureFlagEvaluationException(makeMessage(statusCode, errorDescription)) {

        private companion object {
            @JvmStatic
            private fun makeMessage(statusCode: Int? = null, errorDescription: String? = null): String {
                var msg = "Unable to fetch data"

                msg += if (statusCode != null) {
                    " ($statusCode)."
                } else {
                    "."
                }

                if (errorDescription != null) {
                    msg += " $errorDescription"
                }

                return msg
            }
        }
    }
    class OutOfDate() : FeatureFlagEvaluationException("Remote data is outdated")
    class StaleNotAllowed() : FeatureFlagEvaluationException("Stale data is not allowed")
}
