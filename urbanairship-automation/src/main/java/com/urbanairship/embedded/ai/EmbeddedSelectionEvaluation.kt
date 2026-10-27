/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import androidx.annotation.RestrictTo
import com.urbanairship.ai.Evaluation
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.Usage
import com.urbanairship.json.AirshipJsonSchema
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonMap
import com.urbanairship.json.JsonValue
import com.urbanairship.json.requireField

/**
 * Scores each pending embedded candidate 1–10 against the app's prompt, what each candidate's
 * layout says about itself, and whatever user context is supplied.
 *
 * `requiresContext` stays at its default of `false`: the prompt and the candidates'
 * `content_description` can carry a ranking on their own, so demanding a registered context
 * provider would block selection for apps that don't need one. The narrower guard that
 * actually matters — that the candidates are distinguishable at all — lives in
 * [EmbeddedSelectionRequest.hasAnythingToRankOn].
 *
 * @param request The candidates and the instruction to rank them by.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedSelectionEvaluation public constructor(
    private val request: EmbeddedSelectionRequest
) : Evaluation<EmbeddedSelectionEvaluation.Output, EmbeddedSelectionSubject> {

    /**
     * The model's scores.
     *
     * @param scores One score per candidate id.
     * @param reason The model's stated reason. Never logged — it is derived from user context.
     */
    public data class Output public constructor(
        public val scores: List<CandidateScore>,
        public val reason: String
    ) {
        /**
         * What the model thought of one candidate.
         *
         * @param id The candidate's instance ID.
         * @param score Relevance, 1–10, higher being a better match.
         */
        public data class CandidateScore public constructor(
            public val id: String,
            public val score: Int
        )
    }

    /**
     * The id the model sees for each candidate's instance ID: `"1"`, `"2"`, and so on.
     *
     * A model echoes every id back in its reply, and a 36-character UUID costs an on-device
     * model seconds per candidate to generate.
     */
    private val modelIds: Map<String, String> = request.candidates
        .mapIndexed { index, candidate -> candidate.instanceId to (index + 1).toString() }
        .toMap()

    private val instanceIds: Map<String, String> = modelIds.entries.associate { (instanceId, modelId) ->
        modelId to instanceId
    }

    override val usage: Usage<EmbeddedSelectionSubject> = Usage.embeddedSelection

    override val subject: EmbeddedSelectionSubject = EmbeddedSelectionSubject(
        embeddedId = request.embeddedId,
        pending = request.candidates,
        hints = request.subjectHints
    )

    override val schema: AirshipJsonSchema = AirshipJsonSchema.obj(
        properties = mapOf(
            "scores" to AirshipJsonSchema.array(
                items = AirshipJsonSchema.obj(
                    properties = mapOf(
                        // Constrained to the real ids, so a model that invents one is a schema
                        // failure the framework retries rather than a silent mis-ranking.
                        "id" to AirshipJsonSchema.string(choices = instanceIds.keys.toList()),
                        "score" to AirshipJsonSchema.integer(
                            description = "Relevance score 1-10, higher is a better match"
                        )
                    ),
                    required = listOf("id", "score")
                ),
                description = "A relevance score for each candidate"
            ),
            "reason" to AirshipJsonSchema.string(description = "Brief reason for the scores")
        ),
        required = listOf("scores", "reason")
    )

    override fun instructions(): String = """
        You score content candidates for one user.

        ## Instruction
        <prompt>${request.prompt}</prompt>

        ## Scoring
        - Give every candidate id one score from 1 to 10 for how well it fits the instruction and the user context. Never omit or invent an id.
        - The user context, if any, describes the user. A candidate's description and extras describe only that candidate.
        - 9-10: direct match to the instruction or a stated user interest. 6-8: plausibly relevant. 1-4: mismatch or a competing item, such as dog items for a cat owner.
        - Score every candidate 5 only when neither the instruction nor the user context tells them apart. Without user context, rank on the instruction alone.
    """.trimIndent()

    override fun prompt(context: EvaluationContext): String {
        val candidates = request.candidates.map { candidate ->
            JsonMap.newBuilder()
                .put("id", modelIds.getValue(candidate.instanceId))
                .apply {
                    candidate.contentDescription?.let { put("description", it) }
                    // Extras are nested rather than flattened: they are author-supplied, so a
                    // key like `id` would collide with the instance ID that scores are matched
                    // back by, silently dropping the candidate from the ranking.
                    //
                    // `additional_context` is deliberately absent — it is pooled into the
                    // shared context so every item renders in one section and can be trimmed
                    // by priority. A candidate carries only what identifies it.
                    if (!candidate.extras.isEmpty) {
                        put("extras", candidate.extras)
                    }
                }
                .build()
                .toJsonValue()
        }

        val parts = mutableListOf(
            "Score each of the following candidates according to the system instructions." +
                    "\n\n## Candidates\n${JsonValue.wrap(candidates)}"
        )

        // One section for every context item, whatever supplied it — the app's provider and
        // the layouts' `additional_context` render identically and are indistinguishable to
        // the model by design. Both are expected to be user-framed facts.
        context.renderBullets()?.let { parts.add("## User context\n$it") }

        return parts.joinToString("\n\n")
    }

    @Throws(JsonException::class)
    override fun parseOutput(json: JsonValue): Output {
        val content = json.requireMap()
        return Output(
            scores = content.require("scores").requireList().map { score ->
                val entry = score.requireMap()
                val modelId: String = entry.requireField("id")
                Output.CandidateScore(
                    id = instanceIds[modelId] ?: throw JsonException("Unknown candidate id: $modelId"),
                    score = entry.requireField("score")
                )
            },
            reason = content.requireField("reason")
        )
    }
}
