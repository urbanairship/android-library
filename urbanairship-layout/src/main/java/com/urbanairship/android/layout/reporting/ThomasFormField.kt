package com.urbanairship.android.layout.reporting

import androidx.annotation.RestrictTo
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.FormInputType
import com.urbanairship.android.layout.reporting.ThomasFormField.Type
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

public sealed class ThomasFormField<T>(
    internal val type: Type,
) {

    public abstract val identifier: String
    public abstract val originalValue: T?
    internal abstract val fieldType: FieldType<T>

    internal data class Result<T>(
        val value: T,
        val channels: List<ThomasChannelRegistration>? = null,
        val attributes: Map<AttributeName, AttributeValue>? = null
    )

    internal val status: ThomasFormFieldStatus<T>
        get() {
            return when(val type = fieldType) {
                is FieldType.Async -> type.fetcher.results.value?.status ?: ThomasFormFieldStatus.Pending()
                is FieldType.Instant -> type.result?.let { ThomasFormFieldStatus.Valid(it) } ?: ThomasFormFieldStatus.Invalid()
            }
        }

    public enum class Type(private val value: String) : JsonSerializable {
        FORM("form"),
        NPS_FORM("nps"),
        TOGGLE("toggle"),
        MULTIPLE_CHOICE("multiple_choice"),
        SINGLE_CHOICE("single_choice"),
        TEXT("text_input"),
        EMAIL("email_input"),
        SMS("sms_input"),
        SCORE("score");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(value)
    }

    internal open fun formData(withState: Boolean = true): JsonMap {
        val builder = JsonMap.newBuilder()

        builder.put(KEY_TYPE, type)
        builder.put(KEY_VALUE, JsonValue.wrapOpt(originalValue))
        if (withState) {
            builder.put(KEY_STATUS, status.toJson(type))
        }

        return builder.build()
    }

    internal open val formData: JsonMap
        get() = jsonMapOf(
            KEY_TYPE to type,
            KEY_VALUE to JsonValue.wrapOpt(originalValue),
            KEY_STATUS to status.toJson(type)
        )

    public fun jsonValue(): JsonValue? =
        JsonValue.wrapOpt(originalValue).let {
            if (it != JsonValue.NULL) it else null
        }

    public data class Toggle(
        override val identifier: String,
        override val originalValue: Boolean?,
        override val fieldType: FieldType<Boolean>
    ) : ThomasFormField<Boolean>(Type.TOGGLE)

    public data class CheckboxController(
        override val identifier: String,
        override val originalValue: Set<JsonValue>?,
        override val fieldType: FieldType<Set<JsonValue>>
    ) : ThomasFormField<Set<JsonValue>>(Type.MULTIPLE_CHOICE)

    public data class RadioInputController(
        override val identifier: String,
        override val originalValue: JsonValue?,
        override val fieldType: FieldType<JsonValue>
    ) : ThomasFormField<JsonValue>(
        Type.SINGLE_CHOICE,
    )

    public data class TextInput(
        val textInput: FormInputType,
        override val identifier: String,
        override val originalValue: String?,
        override val fieldType: FieldType<String>
    ) : ThomasFormField<String>(when(textInput) {
        FormInputType.EMAIL -> Type.EMAIL
        FormInputType.SMS -> Type.SMS
        else -> Type.TEXT
    })

    public data class Score(
        override val identifier: String,
        override val originalValue: Int?,
        override val fieldType: FieldType<Int>
    ) : ThomasFormField<Int>(Type.SCORE)

    public sealed class BaseForm(
        type: Type,
        override val identifier: String,
        override val originalValue: Set<ThomasFormField<*>>,
        override val fieldType: FieldType<Set<ThomasFormField<*>>>
    ) : ThomasFormField<Set<ThomasFormField<*>>>(type), JsonSerializable {
        protected abstract val responseType: String?

        protected fun childrenJson(withState: Boolean = true): JsonSerializable {
            val builder: JsonMap.Builder = JsonMap.newBuilder()
            for (child in originalValue) {
                builder.putOpt(child.identifier, child.formData(withState))
            }
            return builder.build()
        }

        override fun toJsonValue(): JsonValue = toJsonValue(withState = true)

        public fun toJsonValue(withState: Boolean = true): JsonValue =
            jsonMapOf(identifier to formData(withState)).toJsonValue()
    }

    public data class Form(
        override val identifier: String,
        override val responseType: String?,
        val children: Set<ThomasFormField<*>>,
        override val fieldType: FieldType<Set<ThomasFormField<*>>>
    ) : BaseForm(Type.FORM, identifier, children, fieldType = fieldType) {

        override fun formData(withState: Boolean): JsonMap = jsonMapOf(
            KEY_TYPE to type,
            KEY_CHILDREN to childrenJson(withState),
            KEY_RESPONSE_TYPE to responseType
        )
    }

    public data class Nps(
        override val identifier: String,
        private val scoreId: String,
        override val responseType: String?,
        val children: Set<ThomasFormField<*>>,
        override val fieldType: FieldType<Set<ThomasFormField<*>>>
    ) : BaseForm(Type.NPS_FORM, identifier, children, fieldType = fieldType) {

        override fun formData(withState: Boolean): JsonMap = jsonMapOf(
            KEY_TYPE to type,
            KEY_CHILDREN to childrenJson(withState),
            KEY_SCORE_ID to scoreId,
            KEY_RESPONSE_TYPE to responseType
        )
    }

    internal companion object {
        private const val KEY_TYPE: String = "type"
        private const val KEY_VALUE: String = "value"
        private const val KEY_STATUS: String = "status"
        private const val KEY_SCORE_ID: String = "score_id"
        private const val KEY_CHILDREN: String = "children"
        private const val KEY_RESPONSE_TYPE: String = "response_type"

        fun makeAttributes(
            name: AttributeName?,
            value: AttributeValue?
        ): Map<AttributeName, AttributeValue>? {
            if (name == null || value == null) {
                return null
            }

            return mapOf(name to value)
        }
    }

    override fun toString(): String {
        return "${formData.toJsonValue()}"
    }

    public sealed class FieldType<T> {
        internal data class Instant<T>(val result: Result<T>?): FieldType<T>()
        internal data class Async<T>(val fetcher: AsyncValueFetcher<T>): FieldType<T>()

        /** @hide */
        @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
        public companion object {
            public fun <T> just(
                value: T,
                validator: ((T) -> Boolean)? = null,
                channels: List<ThomasChannelRegistration>? = null,
                attributes: Map<AttributeName, AttributeValue>? = null
            ): FieldType<T> {
                return if (validator == null || validator(value)) {
                    Instant(Result(
                        value = value,
                        attributes = attributes,
                        channels = channels))
                } else {
                    Instant(null)
                }
            }
        }

        internal fun cancel() {
            when(this) {
                is Async -> fetcher.cancel()
                is Instant -> {}
            }
        }
    }

    internal class AsyncValueFetcher<T>(
        private val fetchBlock: suspend () -> PendingResult<T>,
        private val processDelay: Duration = 1.seconds,
        private val clock: Clock = Clock.DEFAULT_CLOCK,
        private val taskSleeper: TaskSleeper = TaskSleeper.default
    ) {

        private var lastAttemptTimestamp: Long? = null
        private var fetchJob: Deferred<PendingResult<T>>? = null
        private var nextBackOff: Duration? = null

        private val _resultsFlow = MutableStateFlow<PendingResult<T>?>(null)
        val results = _resultsFlow.asStateFlow()

        companion object {
            val INITIAL_BACK_OFF = 3.seconds
            val MAX_BACK_OFF = 15.seconds
        }

        suspend fun fetch(scope: CoroutineScope, retryErrors: Boolean): PendingResult<T> {
            val lastResult = results.value
            if (lastResult != null && (!lastResult.isError || !retryErrors)) {
                return lastResult
            }

            fetchJob?.let { job ->
                if (job.isActive) {
                    return job.await()
                }
            }

            fetchJob?.cancel()

            return initiateFetching(scope).await()
        }

        fun cancel() {
            fetchJob?.cancel()
        }

        private fun initiateFetching(scope: CoroutineScope): Deferred<PendingResult<T>> {
            fetchJob?.let {
                if (it.isActive) {
                    return it
                }
            }

            val isInitialCall = _resultsFlow.value == null

            val job = scope.async {
                try {
                    if (isInitialCall) {
                        taskSleeper.sleep(processDelay)
                        yield()
                    }
                    processBackOff()
                    yield()
                    val result = fetchBlock.invoke()
                    yield()
                    processResult(result)
                } catch (ex: Exception) {
                    processResult(PendingResult.Error())
                }
            }

            fetchJob = job
            return job
        }

        private suspend fun processBackOff() {
            val nextBackOff = nextBackOff ?: return
            val lastAttemptTimestamp = lastAttemptTimestamp ?: return

            val remaining = nextBackOff - (clock.currentTimeMillis() - lastAttemptTimestamp).milliseconds
            if (remaining.isPositive()) {
                taskSleeper.sleep(remaining)
            }
        }

        private fun processResult(result: PendingResult<T>): PendingResult<T> {
            _resultsFlow.update { result }
            lastAttemptTimestamp = clock.currentTimeMillis()

            nextBackOff = if (result.isError) {
                nextBackOff?.let { minOf(it * 2, MAX_BACK_OFF) } ?: INITIAL_BACK_OFF
            } else {
                null
            }

            return result
        }

        sealed class PendingResult<T>() {
            data class Valid<T>(val result: Result<T>): PendingResult<T>()
            class Invalid<T>: PendingResult<T>()
            class Error<T>: PendingResult<T>()

            val isError: Boolean
                get() = this is Error

            val isInvalid: Boolean
                get() = this is Invalid

            val status: ThomasFormFieldStatus<T>
                get() {
                   return when(this) {
                        is Error -> ThomasFormFieldStatus.Error()
                        is Invalid -> ThomasFormFieldStatus.Invalid()
                        is Valid -> ThomasFormFieldStatus.Valid(result)
                    }
                }

            val value: Result<T>?
                get() {
                    return when(this) {
                        is Valid -> result
                        else -> null
                    }
                }
        }
    }
}

internal sealed class ThomasFormFieldStatus<T> {
    data class Valid<T>(val result: ThomasFormField.Result<T>): ThomasFormFieldStatus<T>()
    class Invalid<T> : ThomasFormFieldStatus<T>()
    class Pending<T> : ThomasFormFieldStatus<T>()
    class Error<T> : ThomasFormFieldStatus<T>()

    val isPending: Boolean
        get() = this is Pending

    val isValid: Boolean
        get() = this is Valid

    val isError: Boolean
        get() = this is Error

    val isInvalid: Boolean
        get() = this is Invalid

    fun makePending(): Pending<T> {
        return Pending()
    }

    fun toJson(type: Type): JsonValue {
        val builder = JsonMap.newBuilder()
        when(this) {
            is Error -> builder.put(KEY_TYPE, STATUS_ERROR)
            is Invalid -> builder.put(KEY_TYPE, STATUS_INVALID)
            is Pending -> builder.put(KEY_TYPE, STATUS_PENDING)
            is Valid -> {
                builder.put(KEY_TYPE, STATUS_VALID)
                builder.put(KEY_RESULT, jsonMapOf(
                    KEY_TYPE to type,
                    KEY_VALUE to JsonValue.wrap(result.value)
                ))
            }
        }

        return builder.build().toJsonValue()
    }

    private companion object {
        private const val STATUS_ERROR = "error"
        private const val STATUS_INVALID = "invalid"
        private const val STATUS_PENDING = "pending"
        private const val STATUS_VALID = "valid"
        private const val KEY_TYPE = "type"
        private const val KEY_RESULT = "result"
        private const val KEY_VALUE = "value"
    }
}
