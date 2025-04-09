/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.AirshipDispatchers
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.reporting.ThomasFormFieldStatus
import com.urbanairship.android.layout.util.DelicateLayoutApi
import com.urbanairship.util.Clock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class ThomasForm(
    private val feed: SharedState<State.Form>,
    private val pagerState: SharedState<State.Pager>? = null,
    validationDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
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

        val state = feed.changes.value.copy(status = ThomasFormStatus.VALIDATING)
        feed.update { state }

        val containsPending = state.filteredFields.any { it.value.status.isPending }
        val start = Clock.DEFAULT_CLOCK.currentTimeMillis().milliseconds
        val processResult = state.filteredFields.mapValues { (_, field) ->
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

        feed.update { state.copyWithProcessResult(processResult) }

        val end = Clock.DEFAULT_CLOCK.currentTimeMillis().milliseconds
        if (containsPending && validationMode == FormValidationMode.ON_DEMAND) {
            val remaining = 1.seconds - (end - start)
            if (remaining.isPositive()) {
                delay(remaining)
            }
        }

        return feed.changes.value.status == ThomasFormStatus.VALID
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

        when(val method = value.fieldType) {
            is ThomasFormField.FieldType.Async -> scope.launch {
                val fetchResult = method.fetcher.fetch(this, true)
                yield()
                if (!fetchResult.isError) {
                    feed.update { it.copyWithFormInput(value, predicate) }
                }
            }
            is ThomasFormField.FieldType.Instant -> {}
        }

        feed.update { it.copyWithFormInput(value, predicate) }

        if (validationMode == FormValidationMode.IMMEDIATE) {
            scope.launch {
                validate()
            }
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
