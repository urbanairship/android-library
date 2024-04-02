package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import androidx.annotation.MainThread
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface

internal interface DisplayAdapterInterface {
    @MainThread
    suspend fun getIsReady() : Boolean
    suspend fun waitForReady()

    @MainThread
    suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult
}

internal enum class DisplayResult {
    CANCEL, FINISHED
}
