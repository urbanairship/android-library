/* Copyright Airship and Contributors */

package com.urbanairship.iam.adapter

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.iam.analytics.InAppMessageAnalyticsInterface
import kotlinx.coroutines.flow.StateFlow

internal interface DisplayAdapter {

    val isReady: StateFlow<Boolean>

    @MainThread
    suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class DisplayResult {
    CANCEL, FINISHED
}
