package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue
import kotlin.collections.set

internal class LayoutState(
    val pager: SharedState<State.Pager>?,
    val form: SharedState<State.Form>?,
    val parentForm: SharedState<State.Form>?,
    val checkbox: SharedState<State.Checkbox>?,
    val radio: SharedState<State.Radio>?,
    val layout: SharedState<State.Layout>?,
) {
    fun reportingContext(
        formContext: FormInfo? = null,
        pagerContext: PagerData? = null,
        buttonId: String? = null
    ): LayoutData =
        LayoutData(
            formContext ?: form?.changes?.value?.reportingContext(),
            pagerContext ?: pager?.changes?.value?.reportingContext(),
            buttonId
        )

    companion object {
        @JvmField
        val EMPTY = LayoutState(null, null, null, null, null, null)
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
        val branching: PagerControllerBranching?
    ) : State() {
        val hasNext
            get() = pageIndex < pageIds.size - 1
        val hasPrevious
            get() = pageIndex > 0

        fun copyWithPageIndex(index: Int) =
            if (index == pageIndex) {
                copy()
            } else {
                copy(
                    pageIndex = index,
                    lastPageIndex = pageIndex,
                    completed = completed || (index == pageIds.size - 1),
                    progress = 0
                )
            }

        fun copyWithPageIndexAndResetProgress(index: Int) =
            if (index == pageIndex) {
                copy(
                    progress = 0
                )
            } else {
                copyWithPageIndex(index)
            }

        fun copyWithPageIds(pageIds: List<String>) =
            copy(
                pageIds = pageIds,
                completed = pageIds.size <= 1
            )

        fun copyWithDurations(durations: List<Int?>) =
            copy(durations = durations)

        fun copyWithMediaPaused(isMediaPaused: Boolean) =
            copy(isMediaPaused = isMediaPaused,
                 wasMediaPaused = this.isMediaPaused && !isMediaPaused)

        fun copyWithStoryPaused(isStoryPaused: Boolean) =
            copy(isStoryPaused = isStoryPaused)

        fun copyWithTouchExplorationState(isTouchExplorationEnabled: Boolean) =
            copy(isTouchExplorationEnabled = isTouchExplorationEnabled)

        fun reportingContext(): PagerData =
            PagerData(identifier, pageIndex, pageIds.getOrElse(pageIndex) { "NULL!" }, pageIds.size, completed)
    }

    internal data class Form(
        val identifier: String,
        val formType: FormType,
        val formResponseType: String?,
        val data: Map<String, FormData<*>> = emptyMap(),
        val inputValidity: Map<String, Boolean> = emptyMap(),
        /**
         * Input identifiers that are displayed in the current pager page.
         * If the form is not in a pager, this will contain all input identifiers.
         */
        val displayedInputs: Set<String> = emptySet(),
        val isVisible: Boolean = false,
        val isSubmitted: Boolean = false,
        val isEnabled: Boolean = true,
        val isDisplayReported: Boolean = false
    ) : State() {
        val isValid: Boolean
            get() = inputValidity.isNotEmpty() && inputValidity.values.all { it }

        fun copyWithFormInput(value: FormData<*>): Form {
            return copy(
                data = data + (value.identifier to value),
                inputValidity = inputValidity + (value.identifier to value.isValid),
            )
        }

        fun copyWithDisplayState(identifier: String, isDisplayed: Boolean?): Form {
            return copy(
                displayedInputs = isDisplayed?.let {
                    if (isDisplayed) {
                        displayedInputs + identifier
                    } else {
                        displayedInputs - identifier
                    }
                } ?: displayedInputs
            )
        }

        fun formResult(): ReportingEvent.FormResult =
            ReportingEvent.FormResult(formData(), reportingContext(), attributes())

        fun reportingContext(): FormInfo =
            FormInfo(identifier, formType.value, formResponseType, isSubmitted)

        private fun formData(): FormData.BaseForm =
            when (formType) {
                is FormType.Form ->
                    FormData.Form(identifier, formResponseType, data.values.toSet())
                is FormType.Nps ->
                    FormData.Nps(identifier, formType.scoreId, formResponseType, data.values.toSet())
            }

        private fun attributes(): Map<AttributeName, AttributeValue> {
            val map = mutableMapOf<AttributeName, AttributeValue>()
            for (d in data) {
                val attributeName = d.value.attributeName
                val attributeValue = d.value.attributeValue
                if (attributeName != null && attributeValue != null) {
                    map[attributeName] = attributeValue
                }
            }
            return map
        }
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
    ) : State()
}

@Suppress("UNCHECKED_CAST")
internal fun <T : FormData<*>> State.Form.inputData(identifier: String): T? {
    return data[identifier] as? T
}

internal fun <T : FormData<*>> SharedState<State.Form>.inputData(identifier: String): T? {
    return changes.value.inputData(identifier)
}
