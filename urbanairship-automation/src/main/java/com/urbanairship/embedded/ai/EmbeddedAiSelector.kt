/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import androidx.annotation.RestrictTo
import com.urbanairship.UALog
import com.urbanairship.ai.EvaluationResult
import com.urbanairship.ai.InternalAirshipAi
import com.urbanairship.ai.ModelAvailability
import com.urbanairship.ai.Usage

/**
 * Ranks the pending content for an embedded view with the model.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface EmbeddedAiSelector {

    /**
     * Whether the embedded-selection model can run right now.
     *
     * Checked before scheduling work so an absent model never delays display.
     */
    public val isAvailable: Boolean

    /**
     * Ranks the candidates best first.
     *
     * @param request The candidates and how to rank them.
     * @return The candidates' instance IDs in display order — deduped, real candidates only —
     * or `null` for no opinion, which leaves the caller on its fallback selection.
     */
    public suspend fun rank(request: EmbeddedSelectionRequest): List<String>?
}

/**
 * [EmbeddedAiSelector] backed by the SDK's AI manager.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class DefaultEmbeddedAiSelector public constructor(
    private val ai: InternalAirshipAi
) : EmbeddedAiSelector {

    override val isAvailable: Boolean
        get() = try {
            ai.model(Usage.embeddedSelection)?.availability == ModelAvailability.Available
        } catch (e: Exception) {
            // App-implemented getter; a throw here means "can't use it", not "crash the caller".
            UALog.w(e) { "Embedded selection model availability threw" }
            false
        }

    override suspend fun rank(request: EmbeddedSelectionRequest): List<String>? {
        if (request.candidates.isEmpty()) {
            return null
        }

        // All-or-nothing, never per-candidate: if even one candidate is describable the model
        // runs and every candidate goes into the prompt, however thin. Only when NOTHING is
        // describable is the run skipped — with bare UUIDs the instructions say score
        // everything 5, which is the fallback's own ordering, so the round trip buys nothing.
        //
        // This is not a filter: a candidate the model declines to score still lands in the
        // ranking, at the end and in priority order.
        if (!request.hasAnythingToRankOn) {
            UALog.d { "Embedded AI selection has nothing to rank on, using fallback" }
            return null
        }

        val output = when (val result = ai.evaluate(
            evaluation = EmbeddedSelectionEvaluation(request),
            additionalContext = request.layoutContext
        )) {
            is EvaluationResult.Completed -> result.value
            is EvaluationResult.Skipped -> {
                UALog.d { "Embedded AI selection skipped: ${result.reason}" }
                return null
            }
            is EvaluationResult.Failed -> {
                UALog.w(result.error) { "Embedded AI selection failed" }
                return null
            }
        }

        val ranking = request.order(output.scores)

        // On `scored`, not on the whole ranking: the unscored tail covers every candidate, so
        // the full list is empty only when there were no candidates at all. A model that
        // returned nothing usable has no opinion, and the fallback — which may be an
        // allow-list or a comparator — decides instead of priority order standing in for it.
        if (ranking.scored.isEmpty()) {
            UALog.w { "Embedded AI selection returned no usable scores" }
            return null
        }

        val ids = ranking.scored + ranking.unscored

        val threshold = request.minScoreThreshold
        if (threshold != null) {
            val topScore = output.scores.firstOrNull { it.id == ids.first() }?.score ?: 0
            if (topScore < threshold) {
                UALog.d {
                    "Embedded AI top score $topScore below threshold $threshold, using fallback"
                }
                return null
            }
        }

        // Instance IDs are generated per pending instance and carry nothing about the user.
        // The model's stated reason does, so it stays out.
        UALog.d { "Embedded AI ranking $ids" }
        return ids
    }
}

/**
 * A ranking split by whether the model actually scored the candidate.
 *
 * @param scored The scored candidates' instance IDs, best first.
 * @param unscored The instance IDs the model didn't score, in priority order.
 */
private class Ranking(val scored: List<String>, val unscored: List<String>)

/**
 * Orders the candidates from the model's scores.
 *
 * Scores for ids that aren't candidates are dropped, and a repeated id keeps only its first
 * score. Candidates the model didn't score are not dropped — they follow the scored ones, in
 * priority order — so the ranking always covers the whole pending set.
 *
 * @param scores The model's scores.
 * @return The scored and unscored instance IDs, each in display order.
 */
private fun EmbeddedSelectionRequest.order(
    scores: List<EmbeddedSelectionEvaluation.Output.CandidateScore>
): Ranking {
    val priorities = candidates.associate { it.instanceId to it.priority }
    val seen = mutableSetOf<String>()

    val scored = scores
        .filter { priorities.containsKey(it.id) && seen.add(it.id) }
        .sortedWith(
            when (strategy) {
                EmbeddedSelectionStrategy.SCORE_THEN_PRIORITY ->
                    compareByDescending<EmbeddedSelectionEvaluation.Output.CandidateScore> { it.score }
                        .thenBy { priorities.getValue(it.id) }
                EmbeddedSelectionStrategy.PRIORITY_THEN_SCORE ->
                    compareBy<EmbeddedSelectionEvaluation.Output.CandidateScore> { priorities.getValue(it.id) }
                        .thenByDescending { it.score }
            }
        )
        .map { it.id }

    val unscored = candidates
        .filterNot { seen.contains(it.instanceId) }
        .sortedBy { it.priority }
        .map { it.instanceId }

    return Ranking(scored = scored, unscored = unscored)
}
