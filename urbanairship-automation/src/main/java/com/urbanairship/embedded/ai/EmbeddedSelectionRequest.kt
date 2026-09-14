/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import androidx.annotation.RestrictTo
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.embedded.AirshipEmbeddedInfo

/**
 * One request to rank the pending content for an embedded view.
 *
 * @param embeddedId The embedded ID being selected for.
 * @param prompt The app-supplied instruction describing how to rank the candidates.
 * @param candidates The candidates to rank, also handed to the app's context provider as
 * [EmbeddedSelectionSubject.pending] so it can build context aware of the actual choice.
 * @param strategy How model scores and candidate priorities combine into the final order.
 * @param minScoreThreshold The score the winner must reach for the ranking to be used at all.
 * @param subjectHints Hints carried on the subject handed to the app's context provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class EmbeddedSelectionRequest public constructor(
    public val embeddedId: String,
    public val prompt: String,
    public val candidates: List<AirshipEmbeddedInfo>,
    public val strategy: EmbeddedSelectionStrategy = EmbeddedSelectionStrategy.SCORE_THEN_PRIORITY,
    public val minScoreThreshold: Int? = null,
    public val subjectHints: Map<String, String> = emptyMap()
) {

    /**
     * The candidates' authored `content_description.additional_context`, folded into one
     * context the caller appends after the app provider's.
     *
     * Pooled rather than attached per candidate so every context item — app-supplied or
     * layout-supplied — renders in one section and rides the same trimmed channel, which is
     * what makes the authored priority mean anything. The trade is attribution: an item reads
     * as background for the whole decision, not as a fact about the one candidate that
     * declared it.
     *
     * Deduped by content, because sibling layouts in a campaign routinely repeat a line. A
     * repeat keeps its most important (lowest) priority and its first-seen position.
     */
    internal val layoutContext: EvaluationContext
        get() {
            val priorities = LinkedHashMap<String, Double>()

            candidates
                .flatMap { it.additionalContext }
                .filter { it.content.isNotEmpty() }
                .forEach { item ->
                    val existing = priorities[item.content]
                    priorities[item.content] =
                        if (existing == null) item.priority else minOf(existing, item.priority)
                }

            return EvaluationContext(
                priorities.map { (content, priority) -> EvaluationContext.Item(content, priority) }
            )
        }

    /**
     * Whether any candidate carries something that could set it apart from the others.
     *
     * An instance ID alone is not something to reason about — it is a UUID.
     * `additionalContext` deliberately does not count: it is pooled into the shared context,
     * so it is background for the whole decision and identical for every candidate. Only what
     * stays on a candidate can differentiate it.
     */
    internal val hasAnythingToRankOn: Boolean
        get() = candidates.any {
            !it.contentDescription.isNullOrEmpty() || !it.extras.isEmpty
        }
}

/**
 * How model scores and candidate priorities combine into a final ordering.
 *
 * Governs the candidates the model scored. Candidates it declined to score trail all scored
 * ones under either strategy, in priority order among themselves.
 */
public enum class EmbeddedSelectionStrategy {

    /** Score leads; priority breaks ties between equal scores. */
    SCORE_THEN_PRIORITY,

    /** Priority leads; score breaks ties between equal priorities. */
    PRIORITY_THEN_SCORE
}
