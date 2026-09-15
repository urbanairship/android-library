/* Copyright Airship and Contributors */
package com.urbanairship.iam.ai

import androidx.annotation.RestrictTo
import com.urbanairship.ai.Evaluation
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.Usage
import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

/**
 * Asks the model whether a condition authored on the schedule holds for this user, so a message
 * can be held back when it doesn't.
 *
 * Biased towards showing: only a signal that clearly contradicts the condition suppresses. A
 * context that is missing, silent, or only loosely related shows the message, so a model with
 * nothing to go on can't quietly stop a campaign.
 *
 * @param condition The authored condition the user must satisfy.
 * @param subject The message being considered.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class InAppMessageSuppressionEvaluation public constructor(
    private val condition: String,
    override val subject: InAppMessageSuppressionSubject
) : Evaluation<InAppMessageSuppressionEvaluation.Output, InAppMessageSuppressionSubject> {

    /**
     * The model's decision.
     *
     * @param allow Whether to show the message.
     * @param reason The model's stated reason. Never logged or reported — an app reaches it
     * through its own [com.urbanairship.ai.EvaluationObserver].
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public data class Output public constructor(
        public val allow: Boolean,
        public val reason: String
    )

    override val usage: Usage<InAppMessageSuppressionSubject> = Usage.inAppMessageSuppression

    override val schema: AirshipJsonSchema = AirshipJsonSchema.obj(
        properties = mapOf(
            "allow" to AirshipJsonSchema.boolean(
                description = "Whether to show the in-app message to this user"
            ),
            "reason" to AirshipJsonSchema.string(
                description = "Brief reason for the decision"
            )
        ),
        required = listOf("allow", "reason")
    )

    override fun instructions(): String = """
        You are a gate that decides whether to show a single in-app message to the current user. Show the message only when this condition holds for the user; otherwise do not show it:

        <condition>$condition</condition>

        Decide using only the user context and attributes given in the prompt, reasoning about what those signals imply rather than matching them literally. Set allow=true to show the message and allow=false to hide it.

        - Set allow=false only when the context contains a signal that clearly contradicts the condition (for example, the user's stated interests or behavior are directly at odds with it).
        - In every other case — including when the context is missing, silent, or only weakly related to the condition — set allow=true. Do not invent facts, and do not treat the mere absence of a signal as proof the condition is unmet.

        Consider only this condition; ignore unrelated context.
    """.trimIndent()

    override fun prompt(context: EvaluationContext): String {
        // The condition governs the decision and lives in instructions(), deliberately not
        // repeated here. `hints` are the provider's alone and are never rendered.
        val parts = mutableListOf("Message name: ${subject.name}")

        subject.extras
            ?.map
            ?.takeUnless { it.isEmpty }
            ?.let { parts.add("Message Extras: $it") }

        parts.add("Message priority: ${subject.priority}")

        context.renderBullets()?.let { parts.add("User context:\n$it") }

        return parts.joinToString("\n\n")
    }

    @Throws(JsonException::class)
    override fun parseOutput(json: JsonValue): Output {
        val content = json.requireMap()
        return Output(
            allow = content.requireField("allow"),
            reason = content.requireField("reason")
        )
    }
}
