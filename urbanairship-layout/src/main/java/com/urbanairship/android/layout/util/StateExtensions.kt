package com.urbanairship.android.layout.util

import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.ViewPropertyOverride
import com.urbanairship.json.toJsonMap

internal fun <T> State.Layout.resolveOptional(
    overrides: List<ViewPropertyOverride<T>>?,
    default: T? = null
): T? {
    if (overrides.isNullOrEmpty()) {
        return default
    }

    val json = this.state.toJsonMap()
    val override = overrides.firstOrNull {
        it.whenStateMatcher?.apply(json) ?: true
    }

    return if (override != null) {
        override.value
    } else {
        default
    }
}

internal fun <T> State.Layout.resolveRequired(
    overrides: List<ViewPropertyOverride<T>>?,
    default: T
): T {
    if (overrides.isNullOrEmpty()) {
        return default
    }

    val json = this.state.toJsonMap()
    val override = overrides.firstOrNull {
        it.whenStateMatcher?.apply(json) ?: true
    }

    return override?.value ?: default
}
