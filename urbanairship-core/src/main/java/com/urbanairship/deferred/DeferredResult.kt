package com.urbanairship.deferred

import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.AirshipDispatchers
import com.urbanairship.PendingResult
import com.urbanairship.UAirship
import com.urbanairship.audience.DeviceInfoProvider
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class DeferredResult<T> {
    public class Success<T>(public val result: T) : DeferredResult<T>()
    public class RetriableError<T>(public val retryAfter: Long? = null) : DeferredResult<T>()
    public class TimedOut<T>() : DeferredResult<T>()
    public class OutOfDate<T>() : DeferredResult<T>()
    public class NotFound<T>() : DeferredResult<T>()
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class DeferredTriggerContext(
    val type: String,
    val goal: Double,
    val event: JsonValue
) : JsonSerializable {

    private companion object {
        const val KEY_TYPE = "type"
        const val KEY_GOAL = "goal"
        const val KEY_EVENT = "event"
    }

    override fun toJsonValue(): JsonValue {
        return jsonMapOf(
            KEY_TYPE to type,
            KEY_GOAL to goal,
            KEY_EVENT to event
        ).toJsonValue()
    }
}

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public data class DeferredRequest @JvmOverloads constructor(
    val uri: Uri,
    val channelID: String,
    val contactID: String? = null,
    val triggerContext: DeferredTriggerContext? = null,
    val locale: Locale,
    val notificationOptIn: Boolean,
    val appVersionName: String,
    val sdkVersion: String = UAirship.getVersion()
) {

    /**
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        @JvmStatic
        public fun automation(
            uri: Uri,
            infoProvider: DeviceInfoProvider,
            triggerType: String?,
            triggerEvent: JsonValue?,
            triggerGoal: Double,
        ): PendingResult<DeferredRequest> {

            val scope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
            val result = PendingResult<DeferredRequest>()
            val context = if (triggerType != null && triggerEvent != null) {
                DeferredTriggerContext(triggerType, triggerGoal, triggerEvent)
            } else {
                null
            }

            scope.launch {
                result.result = DeferredRequest(
                    uri = uri,
                    channelID = infoProvider.getChannelId(),
                    contactID = infoProvider.getStableContactId(),
                    triggerContext = context,
                    locale = infoProvider.locale,
                    notificationOptIn = infoProvider.isNotificationsOptedIn,
                    appVersionName = infoProvider.appVersionName
                )
            }

            return result
        }
    }
}
