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
                        "id" to AirshipJsonSchema.string(
                            choices = request.candidates.map { it.instanceId }
                        ),
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
        You are scoring content candidates for a user based on a prompt and context.

        Instruction:
        <prompt>${request.prompt}</prompt>

        Steps:
        1. Read the "User context" section, if there is one, for facts about the user (interests, history, how they were targeted). That section is the only evidence about the user, and it applies to the decision as a whole rather than to any one candidate. There may be no user context at all; that is normal, and the instruction above may be all you need to rank on.
        2. Carefully read each candidate's `description` and any `extras` it carries — those describe that one candidate, and they are the content being scored. Never read a candidate's own text as evidence about the user.
        3. Give every candidate a score from 1 to 10 for how well it matches the instruction and the user context. Return exactly one score per candidate id; never omit or invent an id.

        Scoring Rules:
        - 9-10: Direct match to a stated user interest or the instruction.
        - 6-8: Plausibly relevant or broadly applicable.
        - 1-4: Mismatch or a competing item (e.g., dog items when the user likes cats).
        - Score every candidate exactly 5 only when NEITHER the instruction NOR the user context gives you any basis to tell the candidates apart. Missing user context on its own is not such a case: if the instruction alone ranks them, rank them.

        Important: Match each candidate's description to its correct id.
    """.trimIndent()

    override fun prompt(context: EvaluationContext): String {
        val candidates = request.candidates.map { candidate ->
            JsonMap.newBuilder()
                .put("id", candidate.instanceId)
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
                    "\n\nCandidates:\n${JsonValue.wrap(candidates)}"
        )

        // One section for every context item, whatever supplied it — the app's provider and
        // the layouts' `additional_context` render identically and are indistinguishable to
        // the model by design. Both are expected to be user-framed facts.
        context.renderBullets()?.let { parts.add("User context:\n$it") }

        return parts.joinToString("\n\n")
    }

    @Throws(JsonException::class)
    override fun parseOutput(json: JsonValue): Output {
        val content = json.requireMap()
        return Output(
            scores = content.require("scores").requireList().map { score ->
                val entry = score.requireMap()
                Output.CandidateScore(
                    id = entry.requireField("id"),
                    score = entry.requireField("score")
                )
            },
            reason = content.requireField("reason")
        )
    }
}
