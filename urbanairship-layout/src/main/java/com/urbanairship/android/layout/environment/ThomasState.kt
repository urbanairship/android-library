/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.environment

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
): JsonSerializable {

    override fun toJsonValue(): JsonValue {
        val layout = layout ?: return JsonValue.NULL
        val form = form ?: return layout.state.toJsonMap().toJsonValue()

        val formState = ThomasFormField.Form(
            identifier = CURRENT,
            responseType = null,
            children = form.filteredFields.values.toSet(),
            filedType = ThomasFormField.FiledType.just(emptySet())
        )

        return layout.state
            .toMutableMap()
            .apply {
                put(FORMS, jsonMapOf(
                    CURRENT to jsonMapOf(
                        DATA to formState.formData,
                        STATUS to jsonMapOf(TYPE to form.status)
                    )
                ).toJsonValue())
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
    }
}

internal fun makeThomasState(
    formState: SharedState<State.Form>?,
    layoutState: SharedState<State.Layout>?
): StateFlow<ThomasState> {

    val layout = layoutState
        ?: return MutableStateFlow(ThomasState(null, null)).asStateFlow()

    return combineStates(
        flow1 = layout.changes,
        flow2 = formState?.changes ?:MutableStateFlow(null).asStateFlow(),
        transform = ::ThomasState
    )
}
