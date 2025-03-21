/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.AirshipDispatchers
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.util.DelicateLayoutApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

internal class ThomasForm(
    private val feed: SharedState<State.Form>,
    private val pagerState: SharedState<State.Pager>? = null,
    validationDispatcher: CoroutineDispatcher = AirshipDispatchers.newSerialDispatcher()
) {

    val scope = CoroutineScope(validationDispatcher + SupervisorJob())

    val formUpdates = feed.changes

    @OptIn(DelicateLayoutApi::class)
    val isEnabled: Boolean
        get() = feed.value.isEnabled

    @OptIn(DelicateLayoutApi::class)
    suspend fun prepareSubmit(): Pair<ReportingEvent.FormResult, FormInfo>? {
        if (feed.value.isSubmitted) {
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
        when(val method = value.filedType) {
            is ThomasFormField.FiledType.Async -> scope.launch {
                val fetchResult = method.fetcher.fetch(this, true)
                yield()
                if (!fetchResult.isError) {
                    feed.update { it.copyWithFormInput(value, predicate) }
                }
            }
            is ThomasFormField.FiledType.Instant -> {}
        }

        feed.update { it.copyWithFormInput(value, predicate) }
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
