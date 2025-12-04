package com.urbanairship.mockk

import io.mockk.clearMocks

/**
 * Mockk helper to clear invocations on a mock.
 *
 * This is roughly equivalent to Mockito's `Mockito.clearInvocations(mock)`.
 */
public fun clearInvocations(vararg mocks: Any) {
    for (mock in mocks) {
        clearMocks(
            mock,
            answers = false,
            recordedCalls = true,
            childMocks = false,
            verificationMarks = true,
            exclusionRules = false
        )
    }
}
