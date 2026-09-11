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
    val capabilities: ThomasCapabilities
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

                capabilities.ai?.let { status ->
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

/**
 * What the runtime can do right now, as the layout sees it.
 *
 * The [State] fields on [ThomasState] answer "which controllers enclose this node" — each is
 * owned by a controller in the scene, keyed by its identifier, mutated as the user interacts,
 * and restored from disk. These answer "what is available to the whole layout": one value for
 * every node, pushed in from outside, read-only, and never persisted — a restored capability
 * would be stale the moment it loaded.
 *
 * New controller state belongs in [State]. New runtime capabilities belong here.
 *
 * @param ai Which AI models the layout can reach, or `null` until one reports.
 */
internal data class ThomasCapabilities(
    val ai: ThomasAIStatus? = null
) {
    companion object {
        val NONE = ThomasCapabilities()
    }
}

internal fun makeThomasState(
    formState: SharedState<State.Form>?,
    layoutState: SharedState<State.Layout>?,
    pagerState: SharedState<State.Pager>?,
    videoState: SharedState<State.Video>?,
    asyncView: SharedState<State.AsyncView>?,
    capabilities: StateFlow<ThomasCapabilities> =
        MutableStateFlow(ThomasCapabilities.NONE).asStateFlow()
): StateFlow<ThomasState> {

    val layout = layoutState
        ?: return MutableStateFlow(
            ThomasState(null, null, null, null, null, ThomasCapabilities.NONE)
        ).asStateFlow()

    return combineStates(
        flow1 = layout.changes,
        flow2 = formState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow3 = pagerState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow4 = videoState?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow5 = asyncView?.changes ?: MutableStateFlow(null).asStateFlow(),
        flow6 = capabilities,
        transform = ::ThomasState
    )
}
