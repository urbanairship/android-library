/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.UALog
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.reporting.ThomasFormFieldStatus
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.util.Clock
import com.urbanairship.util.TaskSleeper
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class ThomasForm(
    private val feed: SharedState<State.Form>,
    private val pagerState: SharedState<State.Pager>? = null,
    private val clock: Clock = Clock.DEFAULT_CLOCK,
    private val sleeper: TaskSleeper = TaskSleeper.default,
    validationDispatcher: CoroutineDispatcher = Dispatchers.Main
) {

    val scope = CoroutineScope(validationDispatcher + SupervisorJob())

    val validationMode: FormValidationMode
        get() = feed.changes.value.validationMode

    val status: Flow<ThomasFormStatus>
        get() = feed.changes.map { it.status }.distinctUntilChanged()

    val formUpdates = feed.changes

    @OptIn(DelicateLayoutApi::class)
    val isEnabled: Boolean
        get() = feed.value.isEnabled


    init {
        if (validationMode == FormValidationMode.IMMEDIATE) {
            scope.launch {
                status.collect { status ->
                    when(status) {
                        ThomasFormStatus.ERROR,
                        ThomasFormStatus.PENDING_VALIDATION -> {
                            validate()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    suspend fun validate(): Boolean {
        if (feed.changes.value.isSubmitted) {
            return false
        }

        feed.update { it.copy(status = ThomasFormStatus.VALIDATING) }
        val fields = feed.changes.value.filteredFields

        val containsPending = fields.any { it.value.status.isPending }
        val start = clock.currentTimeMillis().milliseconds
        val processResult = fields.mapValues { (_, field) ->
            when(val fieldType = field.fieldType) {
                is ThomasFormField.FieldType.Async<*> -> {
                    fieldType.fetcher.fetch(scope, true)
                    field
                }
                is ThomasFormField.FieldType.Instant<*> -> {
                    field
                }
            }
        }

        feed.update { it.copyWithProcessResult(processResult) }

        val end = clock.currentTimeMillis().milliseconds
        if (containsPending && validationMode == FormValidationMode.ON_DEMAND) {
            val remaining = 1.seconds - (end - start)
            if (remaining.isPositive()) {
                sleeper.sleep(remaining)
            }
        }

        return feed.changes.value.status == ThomasFormStatus.VALID
    }

    fun lastProcessedStatus(identifier: String): ThomasFormFieldStatus<*>? {
        return feed.changes.value.lastProcessedStatus(identifier)
    }

    suspend fun prepareSubmit(): Pair<ReportingEvent.FormResult, FormInfo>? {
        if (!validate()) {
            return null
        }

        val result = suspendCoroutine { cont ->
            feed.update {
                val submitted = it.copy(status = ThomasFormStatus.SUBMITTED)
                cont.resume(Pair(submitted.formResult(), submitted.reportingContext()))
                submitted
            }
        }

        return result
    }

    @OptIn(DelicateLayoutApi::class)
    internal fun <T : ThomasFormField<*>> inputData(identifier: String): T? {
        return feed.value.inputData(identifier)
    }

    fun updateFormInput(
        value: ThomasFormField<*>,
        pageId: String? = null
    ) {
        val predicate = predicate@ {
            val associatedPageId = pageId ?: return@predicate true
            val currentState = @OptIn(DelicateLayoutApi::class) pagerState?.value ?: return@predicate true
            val currentPageId = currentState.currentPageId ?: return@predicate true

            val currentPageIndex = currentState.pageIds.indexOf(currentPageId)
            val associatedPageIndex = currentState.pageIds.indexOf(associatedPageId)

            return@predicate currentPageIndex >= 0 &&       // we have the current page index
                    associatedPageIndex >= 0 &&             // we have the associated page index
                    associatedPageIndex <= currentPageIndex // the associated page has been displayed
        }

        updateFormInput(
            value = value,
            predicate = predicate
        )
    }

    fun updateFormInput(
        value: ThomasFormField<*>,
        predicate: FormFieldFilterPredicate? = null
    ) {
        if (feed.changes.value.isSubmitted) {
            return
        }

        UALog.v("Updating field $value")
        feed.update { it.copyWithFormInput(value, predicate) }

        when(val method = value.fieldType) {
            is ThomasFormField.FieldType.Async -> scope.launch {
                method.fetcher.fetch(this, true)

                // Since ThomasFormField is a class, we just need to have the formState
                // reevaluate its value by calling copy.
                // TODO: separate out FormField and FormFieldResult so we can make
                // them data classes for more predictable results
                feed.update { it.copyWithUpdatedChildValue(value) }
            }

            is ThomasFormField.FieldType.Instant -> {}
        }
    }

    fun updateWithDisplayState(identifier: String, isDisplayed: Boolean) {
        feed.update { it.copyWithDisplayState(identifier, isDisplayed) }
    }

    fun displayReported() {
        feed.update { it.copy(isDisplayReported = true) }
    }

    fun updateStatus(isSubmitted: Boolean? = null, isEnabled: Boolean? = null) {
        feed.update {
            it.copy(
                status = if (isSubmitted == true) ThomasFormStatus.SUBMITTED else it.status,
                isEnabled = isEnabled ?: it.isEnabled
            )
        }
    }
}
