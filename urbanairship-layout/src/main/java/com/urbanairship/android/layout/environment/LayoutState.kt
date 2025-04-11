package com.urbanairship.android.layout.environment

import com.urbanairship.UALog
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.ThomasChannelRegistration
import com.urbanairship.android.layout.model.PageRequest
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.android.layout.reporting.ThomasFormFieldStatus
import com.urbanairship.json.JsonValue
import kotlin.math.max
import kotlinx.coroutines.flow.StateFlow

internal class LayoutState(
    val pager: SharedState<State.Pager>?,
    val checkbox: SharedState<State.Checkbox>?,
    val radio: SharedState<State.Radio>?,
    val layout: SharedState<State.Layout>,
    val thomasState: StateFlow<ThomasState>,
    val thomasForm: ThomasForm?,
    val parentForm: ThomasForm?
) {
    fun reportingContext(
        formContext: FormInfo? = null,
        pagerContext: PagerData? = null,
        buttonId: String? = null
    ): LayoutData =
        LayoutData(
            formContext ?: thomasForm?.formUpdates?.value?.reportingContext(),
            pagerContext ?: pager?.changes?.value?.reportingContext(),
            buttonId
        )

    companion object {
        @JvmField
        val EMPTY = LayoutState(null, null, null,
            layout = SharedState(State.Layout.DEFAULT),
            thomasState = makeThomasState(null, null),
            thomasForm = null,
            parentForm = null
        )
    }

    fun processStateActions(
        actions: List<StateAction>?,
        formValue: Any? = null // JsonSerializable
    ) {
        actions?.forEach { action ->
            when (action) {
                is StateAction.SetFormValue -> layout.let { state ->
                    UALog.v("StateAction: SetFormValue ${action.key} = ${JsonValue.wrapOpt(formValue)}")
                    state.update {
                        it.copy(state = it.state + (action.key to JsonValue.wrapOpt(formValue)))
                    }
                }

                is StateAction.SetState -> layout.let { state ->
                    UALog.v("StateAction: SetState ${action.key} = ${action.value}")
                    state.update {
                        if (action.value?.isNull == false) {
                            it.copy(state = it.state + (action.key to action.value))
                        } else {
                            it.copy(state = it.state - action.key)
                        }
                    }
                }

                StateAction.ClearState -> layout.let { state ->
                    UALog.v("StateAction: ClearState")
                    state.update {
                        it.copy(state = emptyMap())
                    }
                }
            }
        }
    }
}

internal sealed class FormType(
    val value: String
) {
    object Form : FormType("form")
    data class Nps(val scoreId: String) : FormType("nps")

    override fun toString(): String = value
}

internal sealed class State {
    // TODO(stories): We may want to split that out into a separate
    //   state flow to avoid a ton of extra updates to pager state?
    //   Or, we could sprinkle some distinctUntilChanged() calls around and circle back.
    internal data class Pager(
        val identifier: String,
        val pageIndex: Int = 0,
        val lastPageIndex: Int = 0,
        val completed: Boolean = false,
        val pageIds: List<String> = emptyList(),
        val durations: List<Int?> = emptyList(),
        val progress: Int = 0,
        val isMediaPaused: Boolean = false,
        val wasMediaPaused: Boolean = false,
        val isStoryPaused: Boolean = false,
        val isTouchExplorationEnabled: Boolean = false,
        val branching: PagerControllerBranching? = null,
        val isScrollDisabled: Boolean = false
    ) : State() {

        val hasNext
            get() = pageIndex < pageIds.size - 1

        val hasPrevious: Boolean
            get() = pageIndex > 0

        internal fun copyWithPageIndex(index: Int) =
            if (index == pageIndex) {
                copy()
            } else {
                copy(
                    pageIndex = index,
                    lastPageIndex = pageIndex,
                    completed = if (branching == null) { // we want to evaluate complete for pagers with no branching
                        completed || (index == pageIds.size - 1)
                    } else {
                        completed
                    },
                    progress = 0,
                    isScrollDisabled = false
                )
            }

        fun copyWithPageRequest(request: PageRequest): Pager {
            if (isScrollDisabled) {
                return copy(progress = 0)
            }

            val nextIndex = when(request)
            {
                PageRequest.NEXT -> pageIndex + 1
                PageRequest.BACK -> max(pageIndex - 1, 0)
                PageRequest.FIRST -> 0
            }

            return if (pageIndex >= 0 && pageIndex < pageIds.size) {
                copyWithPageIndex(nextIndex)
            } else {
                copy(progress = 0)
            }
        }

        fun copyWithMediaPaused(isMediaPaused: Boolean) =
            copy(isMediaPaused = isMediaPaused,
                 wasMediaPaused = this.isMediaPaused && !isMediaPaused)

        fun copyWithStoryPaused(isStoryPaused: Boolean) =
            copy(isStoryPaused = isStoryPaused)

        fun copyWithTouchExplorationState(isTouchExplorationEnabled: Boolean) =
            copy(isTouchExplorationEnabled = isTouchExplorationEnabled)

        fun reportingContext(): PagerData =
            PagerData(identifier, pageIndex, pageIds.getOrElse(pageIndex) { "NULL!" }, pageIds.size, completed)

        val currentPageId: String?
            get() {
                if (pageIndex < 0 || pageIndex >= pageIds.size) {
                    return null
                }

                return pageIds[pageIndex]
            }

        val previousPageId: String?
            get() {
                if (lastPageIndex < 0 || lastPageIndex >= pageIds.size) {
                    return null
                }

                return pageIds[lastPageIndex]
            }
    }

    internal data class Form(
        val identifier: String,
        val formType: FormType,
        val formResponseType: String?,
        val validationMode: FormValidationMode,
        val status: ThomasFormStatus = ThomasFormStatus.PENDING_VALIDATION,

        /**
         * Input identifiers that are displayed in the current pager page.
         * If the form is not in a pager, this will contain all input identifiers.
         */
        val displayedInputs: Set<String> = emptySet(),
        val isVisible: Boolean = false,
        val isEnabled: Boolean = true,
        val isDisplayReported: Boolean = false,
        private val children: Map<String, Child> = emptyMap(),
    ) : State() {

        val filteredFields: Map<String, ThomasFormField<*>>
            get() = children
                .filterValues { it.predicate?.invoke() ?: true }
                .mapValues { it.value.field }

        val isSubmitted: Boolean = status.isSubmitted

        fun copyWithProcessResult(
            results: Map<String, ThomasFormField<*>>
        ): Form {
            val updatedChildren = children.toMutableMap()
            results.forEach { (key, value) ->
                val existing = updatedChildren[key]
                if (existing != null) {
                    updatedChildren[key] = existing.copy(lastProcessStatus = value.status)
                }
            }

            return copy(
                children = updatedChildren,
                status = evaluateFormStatus(updatedChildren)
            )
        }
        fun copyWithFormInput(
            value: ThomasFormField<*>,
            predicate: FormFieldFilterPredicate? = null,
        ): Form {
            children[value.identifier]?.field?.fieldType?.cancel()

            var updatedChildren = children
            if (updatedChildren[value.identifier]?.lastProcessStatus?.isInvalid != true || !value.status.isInvalid) {
                updatedChildren = updatedChildren + (value.identifier to Child(value, predicate, lastProcessStatus = value.status.makePending()))
            }

            return copy(
                children = updatedChildren,
                status = evaluateFormStatus(updatedChildren)
            )
        }

        fun copyWithDisplayState(identifier: String, isDisplayed: Boolean): Form {
            return copy(
                displayedInputs = if (isDisplayed) {
                    displayedInputs + identifier
                } else {
                    displayedInputs - identifier
                }
            )
        }

        fun lastProcessedStatus(identifier: String): ThomasFormFieldStatus<*>? {
            return children[identifier]?.lastProcessStatus
        }

        fun formResult(): ReportingEvent.FormResult =
            ReportingEvent.FormResult(formData(), reportingContext(), attributes(), channels())

        fun reportingContext(): FormInfo =
            FormInfo(identifier, formType.value, formResponseType, status.isSubmitted)

        @Suppress("UNCHECKED_CAST")
        internal fun <T : ThomasFormField<*>> inputData(identifier: String): T? {
            return children[identifier]?.field as? T
        }

        private fun evaluateFormStatus(
            children: Map<String, Child>
        ): ThomasFormStatus {
            val filtered = children
                .filterValues { it.predicate?.invoke() ?: true }

            return if (filtered.any { it.value.lastProcessStatus.isInvalid }) {
                UALog.v("Updating status to invalid: ${filtered.filter { it.value.lastProcessStatus.isInvalid }}")
                ThomasFormStatus.INVALID
            } else if (filtered.any { it.value.lastProcessStatus.isError }) {
                UALog.v("Updating status to error: ${filtered.filter { it.value.lastProcessStatus.isError }}")
                ThomasFormStatus.ERROR
            } else if (filtered.any { it.value.lastProcessStatus.isPending }) {
                UALog.v("Updating status to pending_validation: ${filtered.filter { it.value.lastProcessStatus.isPending }}")
                ThomasFormStatus.PENDING_VALIDATION
            } else {
                UALog.v("Updating status to valid")
                ThomasFormStatus.VALID
            }
        }

        private fun formData(): ThomasFormField.BaseForm {
            val children = filteredFields.values.toSet()

            return when (formType) {
                is FormType.Form ->
                    ThomasFormField.Form(
                        identifier = identifier,
                        responseType = formResponseType,
                        children = children,
                        fieldType = ThomasFormField.FieldType.just(children))
                is FormType.Nps ->
                    ThomasFormField.Nps(
                        identifier = identifier,
                        scoreId = formType.scoreId,
                        responseType = formResponseType,
                        children = children,
                        fieldType = ThomasFormField.FieldType.just(children))
            }
        }


        private fun attributes(): Map<AttributeName, AttributeValue> {
            val map = mutableMapOf<AttributeName, AttributeValue>()

            filteredFields
                .values
                .mapNotNull { value ->
                    when(val method = value.fieldType) {
                        is ThomasFormField.FieldType.Async -> method.fetcher.results.value?.value
                        is ThomasFormField.FieldType.Instant -> method.result
                    }?.attributes
                }
                .forEach { map.putAll(it) }

            return map.toMap()
        }

        private fun channels(): List<ThomasChannelRegistration> {
            return filteredFields.values
                .mapNotNull { value ->
                    when(val method = value.fieldType) {
                        is ThomasFormField.FieldType.Async -> method.fetcher.results.value?.value
                        is ThomasFormField.FieldType.Instant -> method.result
                    }?.channels
                }
                .flatten()
        }

        internal data class Child(
            val field: ThomasFormField<*>,
            val predicate: FormFieldFilterPredicate? = null,
            val lastProcessStatus: ThomasFormFieldStatus<*>
        )
    }

    internal data class Checkbox(
        val identifier: String,
        val minSelection: Int,
        val maxSelection: Int,
        val selectedItems: Set<JsonValue> = emptySet(),
        val isEnabled: Boolean = true,
    ) : State()

    internal data class Radio(
        val identifier: String,
        val selectedItem: JsonValue? = null,
        val attributeValue: AttributeValue? = null,
        val isEnabled: Boolean = true,
    ) : State()

    internal data class Layout(
        val state: Map<String, JsonValue?> = emptyMap()
    ) : State() {
        companion object {
            val DEFAULT = Layout(emptyMap())
        }
    }
}

internal typealias FormFieldFilterPredicate = () -> Boolean
