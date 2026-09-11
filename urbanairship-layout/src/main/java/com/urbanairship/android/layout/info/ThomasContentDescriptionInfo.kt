/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.info

import androidx.annotation.RestrictTo
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonList
import com.urbanairship.json.JsonMap
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.requireField

/**
 * What a layout says about itself, from its `content_description`.
 *
 * Read when a layout is one of several candidates and something has to choose between them.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ThomasContentDescriptionInfo internal constructor(json: JsonMap) {

    /**
     * A short, self-describing summary of the content, e.g. "Spring sale on cat trees, toys,
     * and grooming supplies".
     */
    public val description: String? = json.optionalField("description")

    /**
     * Layout-authored user context: facts about the person this content was composed for —
     * interests, how they were targeted — that the app's own provider can't know.
     *
     * Pooled with the app provider's context rather than attached to this layout, exactly as
     * a text input's `ai_inference.additional_context` is: one context, rendered in one
     * section, trimmed by priority when the model's input budget is tight. Facts about the
     * *user* go here; what the content is goes in [description].
     */
    public val additionalContext: List<EvaluationContext.Item> =
        json.optionalList("additional_context").toContextItems()

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ThomasContentDescriptionInfo = ThomasContentDescriptionInfo(json)
    }
}

/**
 * Parses an `additional_context` list into context items.
 *
 * Shared by every payload that carries authored context — a text input's `ai_inference` and a
 * layout's `content_description` — so the two can't drift on how `priority` is read.
 *
 * @return The items, empty when the list is absent.
 */
@Throws(JsonException::class)
internal fun JsonList?.toContextItems(): List<EvaluationContext.Item> = this?.map { item ->
    val content = item.requireMap()
    EvaluationContext.Item(
        content = content.requireField("content"),
        priority = content.optionalField<Double>("priority") ?: 0.0
    )
} ?: emptyList()
