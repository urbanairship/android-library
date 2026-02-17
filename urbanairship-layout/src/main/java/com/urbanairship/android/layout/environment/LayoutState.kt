package com.urbanairship.android.layout.environment

import com.urbanairship.AirshipDispatchers
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.LayoutState.StateMutation
import com.urbanairship.android.layout.event.ReportingEvent
import com.urbanairship.android.layout.info.FormValidationMode
import com.urbanairship.android.layout.info.StateControllerInfo
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
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField
import com.urbanairship.json.requireList
import com.urbanairship.json.requireMap
import java.util.UUID
import kotlin.math.max
import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class LayoutState(
    val pager: SharedState<State.Pager>?,
    val checkbox: SharedState<State.Checkbox>?,
    val radio: SharedState<State.Radio>?,
    val score: SharedState<State.Score>?,
    val layout: SharedState<State.Layout>,
    val thomasState: StateFlow<ThomasState>,
    val thomasForm: ThomasForm?,
    val parentForm: ThomasForm?,
    val pagerTracker: PagersViewTracker?
) {
    val scope = CoroutineScope(AirshipDispatchers.IO + SupervisorJob())
    private var runningJobs = mutableMapOf<String, Job>()

    fun reportingContext(
        formContext: FormInfo? = null,
        pagerContext: PagerData? = null,
        buttonId: String? = null
    ): LayoutData {

        val reportPagerContext = (pagerContext ?: pager?.changes?.value?.reportingContext(emptyList()))?.let {
            val history = pagerTracker?.viewedPages(it.identifier) ?: return@let it
            it.copy(history = history)
        }

        return LayoutData(
            formContext ?: thomasForm?.formUpdates?.value?.reportingContext(),
            reportPagerContext,
            buttonId
        )
    }


    companion object {
        val EMPTY = LayoutState(null, null, null,
            layout = SharedState(State.Layout.DEFAULT),
            thomasState = makeThomasState(null, null, null),
            thomasForm = null,
            parentForm = null,
            pagerTracker = null,
            score = null
        )
    }

    fun processStateActions(
        actions: List<StateAction>?,
        formValue: Any? = null // JsonSerializable
    ) {
        actions?.forEach { action ->
            when (action) {
                is StateAction.SetFormValue -> {
                    val value = JsonValue.wrapOpt(formValue)
                    UALog.v("StateAction: SetFormValue ${action.key} = $value")
                    setState(action.key, value)
                }

                is StateAction.SetState -> {
                    UALog.v("StateAction: SetState ${action.key} = ${action.value}, ttl = ${action.ttl}")
                    setState(action.key, action.value, action.ttl)
                }

                StateAction.ClearState -> {
                    UALog.v("StateAction: ClearState")
                    clearState()
                }
            }
        }
    }

    private fun setState(
        key: String,
        value: JsonValue?,
        ttl: Duration? = null
    ) {
        if (value != null && !value.isNull) {
            val mutation = StateMutation(UUID.randomUUID().toString(), key, value)
            layout.update { current ->
                val mutations = current.mutations.toMutableMap()
                mutations[key] = mutation
                current.copy(mutations = mutations)
            }

            if (ttl != null) {
                val job = scope.launch {
                    delay(ttl)
                    removeTempMutation(mutation)
                }
                runningJobs[key]?.cancel()
                runningJobs[key] = job
            }
        } else {
            layout.update { current ->
                val mutations = current.mutations.toMutableMap()
                mutations.remove(key)
                current.copy(mutations = mutations)
            }
        }
    }

    internal data class StateMutation(
        val id: String,
        val key: String,
        val value: JsonValue
    ): JsonSerializable {

        override fun toJsonValue(): JsonValue = jsonMapOf(
            ID to id,
            KEY to key,
            VALUE to value
        ).toJsonValue()

        companion object {
            private const val ID = "id"
            private const val KEY = "key"
            private const val VALUE = "value"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): StateMutation {
                val content = json.requireMap()
                return StateMutation(
                    id = content.requireField(ID),
                    key = content.requireField(KEY),
                    value = content.requireField(VALUE)
                )
            }
        }
    }

    private fun removeTempMutation(mutation: StateMutation) {
        layout.update { current ->
            val mutations = current.mutations.toMutableMap()
            if (mutations[mutation.key]?.equals(mutation) == true) {
                mutations.remove(mutation.key)
            }

            current.copy(mutations = mutations)
        }
    }

    private fun clearState() {
        layout.update {
            it.copy(mutations = emptyMap())
        }
    }
}

internal sealed class FormType(
    val value: String
): JsonSerializable {
    object Form : FormType(TYPE_FORM)
    data class Nps(val scoreId: String) : FormType(TYPE_NPS)

    override fun toString(): String = value

    override fun toJsonValue(): JsonValue {
        val builder = JsonMap.newBuilder().put(TYPE, value)

        when(this) {
            is Nps -> builder.put(SCORE_ID, scoreId)
            else -> {}
        }

        return builder.build().toJsonValue()
    }

    companion object {
        private const val TYPE = "type"
        private const val TYPE_FORM = "form"
        private const val TYPE_NPS = "nps"
        private const val SCORE_ID = "score_id"

        @Throws(JsonException::class)
        fun fromJson(value: JsonValue): FormType {
            val content = value.requireMap()

            return when(val type = content.requireField<String>(TYPE)) {
                TYPE_FORM -> Form
                TYPE_NPS -> Nps(content.requireField(SCORE_ID))
                else -> throw JsonException("Unknown form type: $type")
            }
        }
    }
}

internal sealed class State(val type: Type): JsonSerializable {
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
        val isScrollDisabled: Boolean = false,
        var isScrolling: Boolean = false
    ) : State(Type.PAGER) {

        val hasNext
            get() = pageIndex < pageIds.size - 1 && !isScrollDisabled

        val hasPrevious: Boolean
            get() = pageIndex > 0 && !isScrollDisabled

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

            return if (nextIndex >= 0 && nextIndex < pageIds.size) {
                copyWithPageIndex(nextIndex)
            } else {
                copy(progress = 0)
            }
        }

        fun copyWithScrolling(isScrolling: Boolean): Pager {
            return copy(isScrolling = isScrolling)
        }

        fun copyWithMediaPaused(isMediaPaused: Boolean) =
            copy(isMediaPaused = isMediaPaused,
                 wasMediaPaused = this.isMediaPaused && !isMediaPaused)

        fun copyWithStoryPaused(isStoryPaused: Boolean) =
            copy(isStoryPaused = isStoryPaused)

        fun copyWithTouchExplorationState(isTouchExplorationEnabled: Boolean) =
            copy(isTouchExplorationEnabled = isTouchExplorationEnabled)

        fun reportingContext(history: List<ReportingEvent.PageSummaryData.PageView>): PagerData =
            PagerData(
                identifier = identifier,
                index = pageIndex,
                pageId = pageIds.getOrElse(pageIndex) { "NULL!" },
                count = if (branching == null) { pageIds.size } else { -1 },
                history = history,
                isCompleted = completed)

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

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            IDENTIFIER to identifier,
            PAGE_INDEX to pageIndex,
            LAST_PAGE_INDEX to lastPageIndex,
            COMPLETED to completed,
            PAGE_IDS to pageIds,
            DURATIONS to durations,
            PROGRESS to progress,
            IS_MEDIA_PAUSED to isMediaPaused,
            WAS_MEDIA_PAUSED to wasMediaPaused,
            IS_STORY_PAUSED to isStoryPaused,
            IS_TOUCH_EXPLORATION_ENABLED to isTouchExplorationEnabled,
            BRANCHING to branching,
            IS_SCROLL_DISABLED to isScrollDisabled
        ).toJsonValue()

        companion object {
            private const val IDENTIFIER = "identifier"
            private const val PAGE_INDEX = "page_index"
            private const val LAST_PAGE_INDEX = "last_page_index"
            private const val COMPLETED = "completed"
            private const val PAGE_IDS = "page_ids"
            private const val DURATIONS = "durations"
            private const val PROGRESS = "progress"
            private const val IS_MEDIA_PAUSED = "is_media_paused"
            private const val WAS_MEDIA_PAUSED = "was_media_paused"
            private const val IS_STORY_PAUSED = "is_story_paused"
            private const val IS_TOUCH_EXPLORATION_ENABLED = "is_touch_exploration_enabled"
            private const val BRANCHING = "branching"
            private const val IS_SCROLL_DISABLED = "is_scroll_disabled"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Pager {
                val content = json.requireMap()

                return Pager(
                    identifier = content.requireField(IDENTIFIER),
                    pageIndex = content.requireField(PAGE_INDEX),
                    lastPageIndex = content.requireField(LAST_PAGE_INDEX),
                    completed = content.requireField(COMPLETED),
                    pageIds = content.requireList(PAGE_IDS).map { it.requireString() },
                    durations = content.requireList(DURATIONS).map { it.integer },
                    progress = content.requireField(PROGRESS),
                    isMediaPaused = content.requireField(IS_MEDIA_PAUSED),
                    wasMediaPaused = content.requireField(WAS_MEDIA_PAUSED),
                    isStoryPaused = content.requireField(IS_STORY_PAUSED),
                    isTouchExplorationEnabled = content.requireField(IS_TOUCH_EXPLORATION_ENABLED),
                    branching = content[BRANCHING]?.let(PagerControllerBranching::from),
                    isScrollDisabled = content.requireField(IS_SCROLL_DISABLED)
                )
            }
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
        private val initialChildrenValues: Map<String, JsonValue>
    ) : State(Type.FORM) {

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
                status = evaluateFormStatus(updatedChildren,  allowValid = true),
            )
        }

        fun copyWithFormInput(
            value: ThomasFormField<*>,
            predicate: FormFieldFilterPredicate? = null,
        ): Form {
            children[value.identifier]?.field?.fieldType?.cancel()

            val updatedChildren = children.toMutableMap()
            if (updatedChildren[value.identifier]?.lastProcessStatus?.isInvalid != true || !value.status.isInvalid) {
                updatedChildren[value.identifier] = Child(value, predicate, lastProcessStatus = value.status.makePending())
            }

            return copy(
                children = updatedChildren,
                status = evaluateFormStatus(updatedChildren, allowValid =  false),
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

        fun formResult(): ReportingEvent.FormResultData = ReportingEvent.FormResultData(formData())

        fun reportingContext(): FormInfo =
            FormInfo(identifier, formType.value, formResponseType, status.isSubmitted)

        @Suppress("UNCHECKED_CAST")
        internal fun <T : ThomasFormField<*>> inputData(identifier: String): T? {
            return children[identifier]?.field as? T
        }

        private fun evaluateFormStatus(
            children: Map<String, Child>,
            allowValid: Boolean
        ): ThomasFormStatus {
            val filtered = children
                .filterValues { it.predicate?.invoke() ?: true }

            return if (filtered.any { it.value.lastProcessStatus.isInvalid }) {
                UALog.v("Updating status to invalid: ${filtered.filter { it.value.lastProcessStatus.isInvalid }}")
                ThomasFormStatus.INVALID
            } else if (filtered.any { it.value.lastProcessStatus.isError }) {
                UALog.v("Updating status to error: ${filtered.filter { it.value.lastProcessStatus.isError }}")
                ThomasFormStatus.ERROR
            } else if (filtered.any { it.value.lastProcessStatus.isPending } || !allowValid) {
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
                        fieldType = ThomasFormField.FieldType.just(children),)
                is FormType.Nps ->
                    ThomasFormField.Nps(
                        identifier = identifier,
                        scoreId = formType.scoreId,
                        responseType = formResponseType,
                        children = children,
                        fieldType = ThomasFormField.FieldType.just(children))
            }
        }


        internal fun attributes(): Map<AttributeName, AttributeValue> {
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

        internal fun channels(): List<ThomasChannelRegistration> {
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

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            IDENTIFIER to identifier,
            FORM_TYPE to formType,
            FORM_RESPONSE_TYPE to formResponseType,
            VALIDATION_MODE to validationMode,
            STATUS to status,
            DISPLAYED_INPUTS to displayedInputs,
            IS_VISIBLE to isVisible,
            IS_ENABLED to isEnabled,
            IS_DISPLAY_REPORTED to isDisplayReported,
            CHILDREN to children.mapValues { it.value.field.jsonValue() }
        ).toJsonValue()

        fun getInitialValue(identifier: String): JsonValue? {
            return initialChildrenValues[identifier]
        }

        companion object {
            private const val IDENTIFIER = "identifier"
            private const val FORM_TYPE = "form_type"
            private const val FORM_RESPONSE_TYPE = "form_response_type"
            private const val VALIDATION_MODE = "validation_mode"
            private const val STATUS = "status"
            private const val DISPLAYED_INPUTS = "displayed_inputs"
            private const val IS_VISIBLE = "is_visible"
            private const val IS_ENABLED = "is_enabled"
            private const val IS_DISPLAY_REPORTED = "is_display_reported"
            private const val CHILDREN = "children"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Form {
                val content = json.requireMap()

                return Form(
                    identifier = content.requireField(IDENTIFIER),
                    formType = FormType.fromJson(content.requireField(FORM_TYPE)),
                    formResponseType = content.optionalField(FORM_RESPONSE_TYPE),
                    validationMode = FormValidationMode.from(content.requireField(VALIDATION_MODE)),
                    status = ThomasFormStatus.fromJson(content.requireField(STATUS)),
                    displayedInputs = content.requireList(DISPLAYED_INPUTS)
                        .map { it.requireString() }.toSet(),
                    isVisible = content.requireField(IS_VISIBLE),
                    isEnabled = content.requireField(IS_ENABLED),
                    isDisplayReported = content.requireField(IS_DISPLAY_REPORTED),
                    initialChildrenValues = content.requireMap(CHILDREN).map,
                )
            }
        }
    }

    internal data class Checkbox(
        val identifier: String,
        val minSelection: Int,
        val maxSelection: Int,
        val selectedItems: Set<Selected> = emptySet(),
        val isEnabled: Boolean = true,
    ) : State(Type.CHECKBOX) {

        internal data class Selected(
            val identifier: String?,
            val reportingValue: JsonValue
        ) : JsonSerializable {
            override fun toJsonValue(): JsonValue = jsonMapOf(
                IDENTIFIER to identifier,
                REPORTING_VALUE to reportingValue
            ).toJsonValue()

            companion object {
                private const val IDENTIFIER = "identifier"
                private const val REPORTING_VALUE = "reporting_value"

                @Throws(JsonException::class)
                fun fromJson(json: JsonValue): Selected {
                    val content = json.requireMap()
                    return Selected(
                        identifier = content[IDENTIFIER]?.requireString(),
                        reportingValue = content.requireField(REPORTING_VALUE)
                    )
                }
            }
        }

        fun isSelected(
            identifier: String? = null,
            reportingValue: JsonValue? = null
        ): Boolean {
            return if (reportingValue != null) {
                val toSearch = Selected(identifier, reportingValue)
                selectedItems.any { it == toSearch }
            } else {
                selectedItems.any { it.identifier == identifier }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            IDENTIFIER to identifier,
            MIN_SELECTION to minSelection,
            MAX_SELECTION to maxSelection,
            SELECTED_ITEMS to selectedItems,
            IS_ENABLED to isEnabled
        ).toJsonValue()

        companion object {
            private const val IDENTIFIER = "identifier"
            private const val MIN_SELECTION = "min_selection"
            private const val MAX_SELECTION = "max_selection"
            private const val SELECTED_ITEMS = "selected_items"
            private const val IS_ENABLED = "is_enabled"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Checkbox {
                val content = json.requireMap()
                return Checkbox(
                    identifier = content.requireField(IDENTIFIER),
                    minSelection = content.requireField(MIN_SELECTION),
                    maxSelection = content.requireField(MAX_SELECTION),
                    selectedItems = content.requireList(SELECTED_ITEMS).map(Selected::fromJson).toSet(),
                    isEnabled = content.requireField(IS_ENABLED)
                )
            }
        }
    }

    internal data class Radio(
        val identifier: String,
        val selectedItem: Selected? = null,
        val isEnabled: Boolean = true,
    ) : State(Type.RADIO) {

        fun isSelected(
            identifier: String? = null,
            reportingValue: JsonValue? = null
        ): Boolean {
            return if (identifier != null) {
                selectedItem?.identifier == identifier
            } else {
                selectedItem?.reportingValue == reportingValue
            }
        }

        internal data class Selected(
            val identifier: String?,
            val reportingValue: JsonValue?,
            val attributeValue: AttributeValue?
        ) : JsonSerializable {

            override fun toJsonValue(): JsonValue = jsonMapOf(
                IDENTIFIER to identifier,
                REPORTING_VALUE to reportingValue,
                ATTRIBUTE_VALUE to attributeValue
            ).toJsonValue()

            companion object {
                private const val IDENTIFIER = "identifier"
                private const val REPORTING_VALUE = "reporting_value"
                private const val ATTRIBUTE_VALUE = "attribute_value"

                @Throws(JsonException::class)
                fun fromJson(json: JsonValue): Selected {
                    val content = json.requireMap()
                    return Selected(
                        identifier = content[IDENTIFIER]?.requireString(),
                        reportingValue = content.optionalField(REPORTING_VALUE),
                        attributeValue = content.optionalField(ATTRIBUTE_VALUE)
                    )
                }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            IDENTIFIER to identifier,
            SELECTED_ITEM to selectedItem,
            IS_ENABLED to isEnabled
        ).toJsonValue()

        companion object {
            private const val IDENTIFIER = "identifier"
            private const val SELECTED_ITEM = "selected_item"
            private const val IS_ENABLED = "is_enabled"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Radio {
                val content = json.requireMap()
                return Radio(
                    identifier = content.requireField(IDENTIFIER),
                    selectedItem = content[SELECTED_ITEM]?.let(Selected::fromJson),
                    isEnabled = content.requireField(IS_ENABLED)
                )
            }
        }
    }

    internal data class Score(
        val identifier: String,
        val selectedItem: Selected? = null,
        val isEnabled: Boolean = true,
    ) : State(Type.SCORE) {

        fun isSelected(
            identifier: String? = null,
            reportingValue: JsonValue? = null
        ): Boolean {
            return if (identifier != null) {
                selectedItem?.identifier == identifier
            } else {
                selectedItem?.reportingValue == reportingValue
            }
        }

        internal data class Selected(
            val identifier: String?,
            val reportingValue: JsonValue?,
            val attributeValue: AttributeValue?
        ): JsonSerializable {

            override fun toJsonValue(): JsonValue = jsonMapOf(
                IDENTIFIER to identifier,
                REPORTING_VALUE to reportingValue,
                ATTRIBUTE_VALUE to attributeValue
            ).toJsonValue()

            companion object {
                private const val IDENTIFIER = "identifier"
                private const val REPORTING_VALUE = "reporting_value"
                private const val ATTRIBUTE_VALUE = "attribute_value"

                @Throws(JsonException::class)
                fun fromJson(value: JsonValue): Selected {
                    val content = value.requireMap()
                    return Selected(
                        identifier = content[IDENTIFIER]?.requireString(),
                        reportingValue = content.optionalField(REPORTING_VALUE),
                        attributeValue = content[ATTRIBUTE_VALUE]
                    )
                }
            }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            IDENTIFIER to identifier,
            SELECTED_ITEM to selectedItem,
            IS_ENABLED to isEnabled
        ).toJsonValue()

        companion object {
            private const val IDENTIFIER = "identifier"
            private const val SELECTED_ITEM = "selected_item"
            private const val IS_ENABLED = "is_enabled"

            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Score {
                val content = value.requireMap()
                return Score(
                    identifier = content.requireField(IDENTIFIER),
                    selectedItem = content[SELECTED_ITEM]?.let(Selected::fromJson),
                    isEnabled = content.requireField(IS_ENABLED)
                )
            }
        }
    }

    internal data class Layout(
        val identifier: String,
        var mutations: Map<String, StateMutation> = emptyMap()
    ) : State(Type.LAYOUT) {

        var state: Map<String, JsonValue> = run {
            mutations.mapValues { it.value.value }
        }

        override fun toJsonValue(): JsonValue = jsonMapOf(
            TYPE to type,
            MUTATIONS to mutations,
            IDENTIFIER to identifier
        ).toJsonValue()

        companion object {
            val DEFAULT = Layout(identifier = StateControllerInfo.IDENTIFIER)

            private const val MUTATIONS = "mutations"
            private const val IDENTIFIER = "identifier"

            @Throws(JsonException::class)
            fun fromJson(json: JsonValue): Layout {
                val content = json.requireMap()

                val mutations: Map<String, StateMutation> = content
                    .optionalMap(MUTATIONS)
                    ?.map
                    ?.mapValues { StateMutation.fromJson(it.value) }
                    ?: emptyMap()

                return Layout(
                    identifier = content.requireField(IDENTIFIER),
                    mutations = mutations
                )
            }
        }

        fun copyWithState(state: JsonMap): Layout {
            val mutations = state.associate {
                it.key to StateMutation(UUID.randomUUID().toString(), it.key, it.value)
            }

            return copy(mutations = mutations)
        }
    }

    enum class Type(private val jsonValue: String): JsonSerializable {
        PAGER("pager"),
        FORM("form"),
        CHECKBOX("checkbox"),
        RADIO("radio"),
        SCORE("score"),
        LAYOUT("layout");

        override fun toJsonValue(): JsonValue = JsonValue.wrap(jsonValue)

        companion object {
            @Throws(JsonException::class)
            fun fromJson(value: JsonValue): Type {
                val content = value.requireString()
                return Type.entries.firstOrNull { it.jsonValue == content }
                    ?: throw JsonException("Unknown state type: $content")
            }
        }
    }

    companion object {
        private const val TYPE = "type"

        @Throws(JsonException::class)
        fun fromJson(json: JsonValue): State {
            val type = json.requireMap().require(TYPE).let(Type::fromJson)

            return when(type) {
                Type.PAGER -> Pager.fromJson(json)
                Type.FORM -> Form.fromJson(json)
                Type.CHECKBOX -> Checkbox.fromJson(json)
                Type.RADIO -> Radio.fromJson(json)
                Type.SCORE -> Score.fromJson(json)
                Type.LAYOUT -> Layout.fromJson(json)
            }
        }
    }
}

internal typealias FormFieldFilterPredicate = () -> Boolean
