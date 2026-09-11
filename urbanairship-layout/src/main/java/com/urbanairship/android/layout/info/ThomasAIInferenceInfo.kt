/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.info

import com.urbanairship.ai.EvaluationContext
import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.optionalField
import com.urbanairship.json.optionalList
import com.urbanairship.json.optionalMap
import com.urbanairship.json.requireField

/**
 * The `ai_inference` payload on a text input: what to derive from the user's text, and the
 * shape the model must answer in.
 */
internal class ThomasAIInferenceInfo(json: JsonMap) {

    /** Instruction describing what to derive from the user's text. */
    val prompt: String = json.requireField("prompt")

    /** Expected output shape for the inference. */
    val outputSchema: AirshipJsonSchema = AirshipJsonSchema.fromJson(json.requireField<JsonValue>("output_schema"))

    /**
     * Layout-authored context appended after whatever the app's context provider returns, so
     * it wins priority ties when the model trims.
     */
    val additionalContext: EvaluationContext = EvaluationContext(
        json.optionalList("additional_context")?.map { item ->
            val content = item.requireMap()
            EvaluationContext.Item(
                content = content.requireField("content"),
                priority = content.optionalField<Double>("priority") ?: 0.0
            )
        } ?: emptyList()
    )

    /**
     * Extra data carried on the subject handed to the app's context provider. Not added to the
     * prompt — the app decides whether and how to use it.
     */
    val subjectHints: Map<String, String> = json.optionalMap("subject_hints")
        ?.map
        ?.mapValues { it.value.requireString() }
        ?: emptyMap()

    internal companion object {

        @Throws(JsonException::class)
        fun fromJson(json: JsonMap): ThomasAIInferenceInfo = ThomasAIInferenceInfo(json)
    }
}
