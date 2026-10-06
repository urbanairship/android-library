/* Copyright Airship and Contributors */

package com.urbanairship.debug.ui.ai

import com.urbanairship.ai.EvaluationContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.jsonMapOf
import com.urbanairship.json.optionalField
import com.urbanairship.json.requireField
import java.util.UUID

/** One editable context item in a debug sandbox (mirrors [EvaluationContext.Item]). */
internal data class AirshipDebugContextItem(
    val id: String = UUID.randomUUID().toString(),
    val content: String = "",
    val priority: Double = 0.0
)

/** How a debug sandbox combines its editable items with the app's registered provider. */
internal enum class AirshipDebugContextMode(val label: String) {
    /** Use only the app's registered context provider — production behavior. */
    ProviderOnly("Provider"),

    /** Provider context plus the editable items below (items win priority ties). */
    Append("Provider + custom"),

    /** Ignore the provider; use only the editable items. */
    Override("Custom only")
}

/** The non-empty items as an [EvaluationContext]. */
internal fun List<AirshipDebugContextItem>.toEvaluationContext(): EvaluationContext = EvaluationContext(
    items = this
        .filter { it.content.isNotBlank() }
        .map { EvaluationContext.Item(content = it.content, priority = it.priority) }
)

// region JSON (for AirshipDebugAIStore-backed persistence)

internal fun AirshipDebugContextItem.toJson(): JsonValue = jsonMapOf(
    "id" to id,
    "content" to content,
    "priority" to priority
).toJsonValue()

internal fun contextItemFromJson(json: JsonValue): AirshipDebugContextItem? = try {
    val map = json.requireMap()
    AirshipDebugContextItem(
        id = map.optionalField<String>("id") ?: UUID.randomUUID().toString(),
        content = map.requireField("content"),
        priority = map.optionalField<Double>("priority") ?: 0.0
    )
} catch (e: JsonException) {
    null
}

internal fun List<AirshipDebugContextItem>.toJsonList(): JsonValue =
    JsonValue.wrapOpt(map { it.toJson() })

internal fun contextItemsFromJson(json: JsonValue?): List<AirshipDebugContextItem> =
    json?.list?.mapNotNull(::contextItemFromJson) ?: emptyList()

internal fun AirshipDebugContextMode.toJson(): JsonValue = JsonValue.wrap(name)

internal fun contextModeFromJson(json: JsonValue?): AirshipDebugContextMode {
    val name = json?.string ?: return AirshipDebugContextMode.ProviderOnly
    return try {
        AirshipDebugContextMode.valueOf(name)
    } catch (e: IllegalArgumentException) {
        AirshipDebugContextMode.ProviderOnly
    }
}

// endregion
