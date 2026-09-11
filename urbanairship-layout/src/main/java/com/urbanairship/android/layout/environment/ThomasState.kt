/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

import com.urbanairship.UALog
import com.urbanairship.android.layout.ai.ThomasAIStatus
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.info.ViewPropertyOverride
import com.urbanairship.android.layout.reporting.ThomasFormField
import com.urbanairship.json.JsonSerializable
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.toJsonMap
import com.urbanairship.util.combineStates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class ThomasState(
    val layout: State.Layout?,
    val form: State.Form?,
    val pager: State.Pager?,
    val video: State.Video?,
    val asyncView: State.AsyncView?,
    val ai: ThomasAIStatus?
): JsonSerializable {

    override fun toJsonValue(): JsonValue {
        val layout = layout ?: return JsonValue.NULL

        return layout.state
            .toMutableMap()
            .apply {
                form?.let { form ->
                    val formState = when (form.formType) {
                        FormType.Form -> ThomasFormField.Form(
                            identifier = CURRENT,
                            responseType = null,
                            children = form.filteredFields.values.toSet(),
                            fieldType = ThomasFormField.FieldType.just(emptySet())
                        )
                        is FormType.Nps -> ThomasFormField.Nps(
                            identifier = CURRENT,
                            responseType = null,
                            children = form.filteredFields.values.toSet(),
                            fieldType = ThomasFormField.FieldType.just(emptySet()),
                            scoreId = form.formType.scoreId
                        )
                    }

                    put(FORMS, jsonMapOf(
                        CURRENT to jsonMapOf(
                            DATA to formState.formData(),
                            STATUS to jsonMapOf(TYPE to form.status)
                        )
                    ).toJsonValue())
                }

                pager?.let { pager ->
                    put(PAGER, jsonMapOf(
                        CURRENT to jsonMapOf(
                            PAUSED to pager.isManuallyPaused
                        )
                    ).toJsonValue())
                }

                video?.let { video ->
                    val state = video.current
                    put(VIDEO, jsonMapOf(
                        CURRENT to state
                    ).toJsonValue())
                }

                asyncView?.let { state ->
                    put(ASYNC_VIEW, jsonMapOf(
                        CURRENT to state
                    ).toJsonValue())
                }

                ai?.let { status ->
                    put(AI, jsonMapOf(
                        CURRENT to status
                    ).toJsonValue())
                }
            }
            .toJsonMap()
            .toJsonValue()
    }

    fun <T> resolveOptional(
        overrides: List<ViewPropertyOverride<T>>?,
        default: T? = null
    ): T? {
        val json = toJsonValue()

        return overrides
            ?.firstOrNull { it.whenStateMatcher?.apply(json) ?: true }
            ?.value
            ?: default
    }

    fun <T> resolveRequired(
        overrides: List<ViewPropertyOverride<T>>?,
        default: T
    ): T {
        return resolveOptional(overrides) ?: default
    }

    private companion object {
        const val FORMS = "\$forms"
        const val CURRENT = "current"
        const val DATA = "data"
        const val STATUS = "status"
        const val TYPE = "type"
        const val PAGER = "\$pagers"
        const val PAUSED = "paused"
        const val VIDEO = "\$video"
        const val ASYNC_VIEW = "\$asyncView"
        const val AI = "\$ai"
    }
}

internal fun makeThomasState(
    formState: SharedState<State.Form>?,
    layoutState: SharedState<State.Layout>?,
    pagerState: SharedState<State.Pager>?,
    videoState: SharedState<State.Video>?,
    asyncView: SharedState<State.AsyncView>?,
    aiStatus: StateFlow<ThomasAIStatus?> = MutableStateFlow(null).asStateFlow()
): StateFlow<ThomasState> {

    val layout = layoutState
        ?: return MutableStateFlow(ThomasState(null, null, null, null, null, null)).asStateFlow()

    // Folded in over a second combine rather than a six-flow one: kotlinx only types `combine`
    // up to five flows, and past that the vararg form makes every argument an unchecked cast.
    val states = combineStates(
        flow1 = layout.changes,
        flow2 = formState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow3 = pagerState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow4 = videoState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow5 = asyncView?.changes ?: MutableStateFlow(null).asStateFlow()
    ) { layoutState, form, pager, video, async ->
        ThomasState(layoutState, form, pager, video, async, ai = null)
    }

    return combineStates(states, aiStatus) { state, ai -> state.copy(ai = ai) }
}
