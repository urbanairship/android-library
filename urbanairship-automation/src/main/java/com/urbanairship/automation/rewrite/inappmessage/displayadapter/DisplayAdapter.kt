package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface
import kotlinx.coroutines.flow.StateFlow

internal interface DisplayAdapter {

    val isReady: StateFlow<Boolean>

    @MainThread
    suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult
}

internal enum class DisplayResult {
    CANCEL, FINISHED
}
