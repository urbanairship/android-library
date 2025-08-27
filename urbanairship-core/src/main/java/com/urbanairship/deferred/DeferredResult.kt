package com.urbanairship.deferred

import android.net.Uri
import androidx.annotation.RestrictTo
import com.urbanairship.Airship
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.requireField
import java.util.Locale
import kotlin.jvm.Throws

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public sealed class DeferredResult<T> {
    public class Success<T>(public val result: T) : DeferredResult<T>()
    public class RetriableError<T>(
        public val retryAfter: Long? = null,
        public val statusCode: Int? = null,
        public val errorDescription: String? = null
    ) : DeferredResult<T>()
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

    /** @hide */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_GOAL = "goal"
        private const val KEY_EVENT = "event"

        @Throws(JsonException::class)
        public fun fromJson(value: JsonValue): DeferredTriggerContext {
            val content = value.requireMap()
            return DeferredTriggerContext(
                type = content.requireField(KEY_TYPE),
                goal = content.requireField(KEY_GOAL),
                event = content.opt(KEY_EVENT)
            )
        }
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
public data class DeferredRequest(
    val uri: Uri,
    val channelId: String,
    val contactId: String? = null,
    val triggerContext: DeferredTriggerContext? = null,
    val locale: Locale,
    val notificationOptIn: Boolean,
    val appVersionName: String,
    val sdkVersion: String = Airship.getVersion()
)
