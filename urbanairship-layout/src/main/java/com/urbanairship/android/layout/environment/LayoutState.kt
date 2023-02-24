package com.urbanairship.android.layout.environment

import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.property.AttributeValue
import com.urbanairship.android.layout.reporting.AttributeName
import com.urbanairship.android.layout.reporting.FormData
import com.urbanairship.android.layout.reporting.FormInfo
import com.urbanairship.android.layout.reporting.LayoutData
import com.urbanairship.android.layout.reporting.PagerData
import com.urbanairship.json.JsonValue

internal class LayoutState(
    val pager: SharedState<State.Pager>?,
    val form: SharedState<State.Form>?,
    val parentForm: SharedState<State.Form>?,
    val checkbox: SharedState<State.Checkbox>?,
    val radio: SharedState<State.Radio>?,
    val layout: SharedState<State.Layout>?,
) {
    fun override(
        pagerState: SharedState<State.Pager>?,
        formState: SharedState<State.Form>?,
        parentForm: SharedState<State.Form>?,
        checkboxState: SharedState<State.Checkbox>?,
        radioState: SharedState<State.Radio>?,
        layoutState: SharedState<State.Layout>?,
    ): LayoutState {
        return LayoutState(
            pager = pagerState ?: this.pager,
            form = formState ?: this.form,
            parentForm = parentForm ?: this.parentForm,
            checkbox = checkboxState ?: this.checkbox,
            radio = radioState ?: this.radio,
            layout = layoutState ?: this.layout
        )
    }

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
    data class Pager(
        val identifier: String,
        val pageIndex: Int = 0,
        val lastPageIndex: Int = 0,
        val completed: Boolean = false,
        val pages: List<String> = emptyList()
    ) : State() {
        val hasNext
            get() = pageIndex < pages.size - 1
        val hasPrevious
            get() = pageIndex > 0

        fun copyWithPageIndex(index: Int) =
            if (index == pageIndex) {
                copy()
            } else {
                copy(
                    pageIndex = index,
                    lastPageIndex = pageIndex,
                    completed = completed || (index == pages.size - 1)
                )
            }

        fun copyWithPageIds(pageIds: List<String>) =
            copy(
                pages = pageIds,
                completed = pageIds.size <= 1
            )

        fun reportingContext(): PagerData =
            PagerData(identifier, pageIndex, pages.getOrElse(pageIndex) { "NULL!" }, pages.size, completed)
    }

    data class Form(
        val identifier: String,
        val formType: FormType,
        val formResponseType: String?,
        val data: Map<String, FormData<*>> = emptyMap(),
        val inputValidity: Map<String, Boolean> = emptyMap(),
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
                inputValidity = inputValidity + (value.identifier to value.isValid)
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

    data class Checkbox(
        val identifier: String,
        val minSelection: Int,
        val maxSelection: Int,
        val selectedItems: Set<JsonValue> = emptySet(),
        val isEnabled: Boolean = true,
    ) : State()

    data class Radio(
        val identifier: String,
        val selectedItem: JsonValue? = null,
        val attributeValue: AttributeValue? = null,
        val isEnabled: Boolean = true,
    ) : State()

    data class Layout(
        val state: Map<String, JsonValue?> = emptyMap()
    ) : State()
}

@Suppress("UNCHECKED_CAST")
internal fun <T : FormData<*>> State.Form.inputData(identifier: String): T? {
    return data[identifier] as? T
}

@Suppress("UNCHECKED_CAST")
internal fun <T : FormData<*>> SharedState<State.Form>.inputData(identifier: String): T? {
    return changes.value.inputData(identifier)
}
