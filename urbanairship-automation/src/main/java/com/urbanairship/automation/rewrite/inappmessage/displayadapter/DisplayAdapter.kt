package com.urbanairship.automation.rewrite.inappmessage.displayadapter

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import com.urbanairship.automation.rewrite.inappmessage.analytics.InAppMessageAnalyticsInterface

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface DisplayAdapterInterface {

    public fun getIsReady() : Boolean
    public suspend fun waitForReady()

    @MainThread
    public suspend fun display(context: Context, analytics: InAppMessageAnalyticsInterface): DisplayResult
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public enum class DisplayResult {
    CANCEL, FINISHED
}
