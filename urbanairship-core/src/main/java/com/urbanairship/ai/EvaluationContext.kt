/* Copyright Airship and Contributors */
package com.urbanairship.ai

/**
 * App-supplied context for a model evaluation, as an ordered list of prioritized items.
 *
 * Context goes to the model that runs the evaluation and nowhere else — Airship never receives,
 * stores, or reports it. An on-device model keeps it on the device; a model you configure sends
 * it wherever that model runs.
 *
 * @param items Context pieces in presentation order.
 */
public data class EvaluationContext @JvmOverloads public constructor(
    public val items: List<Item> = emptyList()
) {

    /**
     * A single piece of context.
     *
     * @param content Self-describing text, inserted into the prompt as-is.
     * @param priority Relative importance, where **lower is more important** — the highest
     * values are dropped first when the prompt exceeds the model's input window. Negatives rank
     * above the `0` default.
     */
    public data class Item @JvmOverloads public constructor(
        public val content: String,
        public val priority: Double = 0.0
    )

    /**
     * Renders the items as a `- item` bullet list in order, skipping empty content.
     *
     * @return The bullet list, or `null` when nothing would be rendered.
     */
    public fun renderBullets(): String? = items
        .filter { it.content.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n") { "- ${it.content}" }

    /**
     * Returns a copy with [other]'s items appended after this context's.
     *
     * Appended items are "later" and so win priority ties when trimming.
     *
     * @param other The context to append.
     * @return The merged context.
     */
    public fun appending(other: EvaluationContext): EvaluationContext = EvaluationContext(items + other.items)

    /**
     * Returns a copy without its least-important item, paired with the item removed, or `null`
     * when already empty.
     *
     * Lower priority values are more important, so the highest value goes first, earliest-first
     * within a value. Reached publicly through
     * [ModelRequest.droppingLowestPriorityContextItem].
     */
    internal fun droppingLowestPriorityItem(): Pair<EvaluationContext, Item>? {
        // Index rather than value, so a NaN priority an app computed can't fail to match
        // itself. Double ordering puts NaN above every number, so it drops first.
        val index = items.indices.maxByOrNull { items[it].priority } ?: return null
        return EvaluationContext(items.filterIndexed { i, _ -> i != index }) to items[index]
    }

    public companion object {
        /** An empty context, used when the host supplies nothing. */
        @JvmField
        public val EMPTY: EvaluationContext = EvaluationContext()
    }
}

/**
 * Supplies the context for an evaluation.
 *
 * Called immediately before each evaluation, on the path to displaying the feature, so keep the
 * work light. The SDK holds the provider until it is replaced or cleared.
 */
public fun interface EvaluationContextProvider<Subject> {

    /**
     * Returns the context for an evaluation.
     *
     * @param subject The feature-specific subject.
     * @return The context, or [EvaluationContext.EMPTY] to contribute nothing — the evaluation still
     * runs.
     */
    public suspend fun provideContext(subject: Subject): EvaluationContext
}

/** Supplies context for usages with no provider of their own, e.g. general profile data. */
public fun interface DefaultEvaluationContextProvider {

    /**
     * Returns the context for an evaluation.
     *
     * @return The context.
     */
    public suspend fun provideContext(): EvaluationContext
}
