/* Copyright Airship and Contributors */
package com.urbanairship.embedded.ai

import com.urbanairship.ai.DefaultEvaluationContextProvider
import com.urbanairship.ai.Evaluation
import com.urbanairship.ai.EvaluationContext
import com.urbanairship.ai.EvaluationContextProvider
import com.urbanairship.ai.EvaluationObserver
import com.urbanairship.ai.EvaluationResult
import com.urbanairship.ai.InternalAirshipAi
import com.urbanairship.ai.ModelAdapter
import com.urbanairship.ai.ModelResolver
import com.urbanairship.ai.Usage
import com.urbanairship.embedded.AirshipEmbeddedInfo
import com.urbanairship.json.jsonMapOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
public class EmbeddedAiSelectorTest {

    private val ai = FakeAi()
    private val selector = DefaultEmbeddedAiSelector(ai)

    private fun candidate(
        id: String,
        priority: Int = 0,
        description: String? = "about $id",
        context: List<EvaluationContext.Item> = emptyList()
    ) = AirshipEmbeddedInfo(
        instanceId = id,
        embeddedId = "home_banner",
        priority = priority,
        contentDescription = description,
        additionalContext = context
    )

    private fun request(
        candidates: List<AirshipEmbeddedInfo>,
        strategy: EmbeddedSelectionStrategy = EmbeddedSelectionStrategy.SCORE_THEN_PRIORITY,
        minScoreThreshold: Int? = null
    ) = EmbeddedSelectionRequest(
        embeddedId = "home_banner",
        prompt = "Prefer time-sensitive offers",
        candidates = candidates,
        strategy = strategy,
        minScoreThreshold = minScoreThreshold
    )

    private fun scores(vararg pairs: Pair<String, Int>) = EvaluationResult.Completed(
        EmbeddedSelectionEvaluation.Output(
            scores = pairs.map { (id, score) ->
                EmbeddedSelectionEvaluation.Output.CandidateScore(id, score)
            },
            reason = "because"
        )
    )

    @Test
    public fun testScoreLeadsAndPriorityBreaksTies(): Unit = runTest {
        ai.result = scores("a" to 5, "b" to 9, "c" to 5)

        assertEquals(
            listOf("b", "c", "a"),
            selector.rank(
                request(
                    listOf(
                        candidate("a", priority = 10),
                        candidate("b", priority = 10),
                        candidate("c", priority = 1)
                    )
                )
            )
        )
    }

    @Test
    public fun testPriorityLeadsAndScoreBreaksTies(): Unit = runTest {
        ai.result = scores("a" to 5, "b" to 9, "c" to 5)

        assertEquals(
            listOf("c", "b", "a"),
            selector.rank(
                request(
                    listOf(
                        candidate("a", priority = 10),
                        candidate("b", priority = 10),
                        candidate("c", priority = 1)
                    ),
                    strategy = EmbeddedSelectionStrategy.PRIORITY_THEN_SCORE
                )
            )
        )
    }

    /** A candidate the model skipped is still displayable, so it lands last, not nowhere. */
    @Test
    public fun testUnscoredCandidatesTrailInPriorityOrder(): Unit = runTest {
        ai.result = scores("a" to 7)

        assertEquals(
            listOf("a", "c", "b"),
            selector.rank(
                request(
                    listOf(
                        candidate("a", priority = 5),
                        candidate("b", priority = 9),
                        candidate("c", priority = 2)
                    )
                )
            )
        )
    }

    @Test
    public fun testInventedAndRepeatedIdsAreIgnored(): Unit = runTest {
        ai.result = scores("ghost" to 10, "a" to 3, "a" to 9)

        assertEquals(
            listOf("a", "b"),
            selector.rank(request(listOf(candidate("a"), candidate("b", priority = 1))))
        )
    }

    @Test
    public fun testThresholdFallsBackWhenTheWinnerIsTooWeak(): Unit = runTest {
        ai.result = scores("a" to 4, "b" to 3)

        assertNull(
            selector.rank(
                request(listOf(candidate("a"), candidate("b")), minScoreThreshold = 5)
            )
        )
    }

    @Test
    public fun testThresholdMetIsUsed(): Unit = runTest {
        ai.result = scores("a" to 5, "b" to 3)

        assertEquals(
            listOf("a", "b"),
            selector.rank(
                request(listOf(candidate("a"), candidate("b")), minScoreThreshold = 5)
            )
        )
    }

    /**
     * With nothing but UUIDs to go on the instructions say score everything 5, which is the
     * fallback's own ordering — so the round trip is skipped rather than paid for.
     */
    @Test
    public fun testNothingToRankOnNeverAsksTheModel(): Unit = runTest {
        assertNull(
            selector.rank(
                request(listOf(candidate("a", description = null), candidate("b", description = null)))
            )
        )
        assertNull(ai.evaluation)
    }

    /** One describable candidate is enough: every candidate then goes to the model. */
    @Test
    public fun testOneDescribableCandidateIsEnough(): Unit = runTest {
        ai.result = scores("a" to 8, "b" to 2)

        assertEquals(
            listOf("a", "b"),
            selector.rank(
                request(listOf(candidate("a"), candidate("b", description = null)))
            )
        )
    }

    /** Extras describe a candidate too, so they count as something to rank on. */
    @Test
    public fun testExtrasCountAsDescribable(): Unit = runTest {
        ai.result = scores("a" to 6)

        val withExtras = AirshipEmbeddedInfo(
            instanceId = "a",
            embeddedId = "home_banner",
            extras = jsonMapOf("tier" to "gold")
        )

        assertEquals(listOf("a"), selector.rank(request(listOf(withExtras))))
    }

    @Test
    public fun testNoCandidatesNeverAsksTheModel(): Unit = runTest {
        assertNull(selector.rank(request(emptyList())))
        assertNull(ai.evaluation)
    }

    @Test
    public fun testSkippedAndFailedGiveNoOpinion(): Unit = runTest {
        ai.result = EvaluationResult.Skipped("no model")
        assertNull(selector.rank(request(listOf(candidate("a")))))

        ai.result = EvaluationResult.Failed(IllegalStateException("boom"))
        assertNull(selector.rank(request(listOf(candidate("a")))))
    }

    /** Layout context is pooled, deduped to its most important priority, order preserved. */
    @Test
    public fun testLayoutContextIsPooledAndDeduped(): Unit = runTest {
        ai.result = scores("a" to 5)

        selector.rank(
            request(
                listOf(
                    candidate("a", context = listOf(
                        EvaluationContext.Item("Interests: cats", 5.0),
                        EvaluationContext.Item("Lapsed buyer", 1.0)
                    )),
                    candidate("b", context = listOf(
                        EvaluationContext.Item("Interests: cats", -2.0),
                        EvaluationContext.Item("", 0.0)
                    ))
                )
            )
        )

        assertEquals(
            listOf(
                EvaluationContext.Item("Interests: cats", -2.0),
                EvaluationContext.Item("Lapsed buyer", 1.0)
            ),
            ai.additionalContext?.items
        )
    }

    private class FakeAi : InternalAirshipAi {
        var result: EvaluationResult<*> = EvaluationResult.Skipped("unset")

        var evaluation: Evaluation<*, *>? = null
            private set
        var additionalContext: EvaluationContext? = null
            private set

        @Suppress("UNCHECKED_CAST")
        override suspend fun <Output, Subject> evaluate(
            evaluation: Evaluation<Output, Subject>,
            additionalContext: EvaluationContext
        ): EvaluationResult<Output> {
            this.evaluation = evaluation
            this.additionalContext = additionalContext
            return result as EvaluationResult<Output>
        }

        override val defaultModel: ModelAdapter? = null
        override fun model(usage: Usage<*>): ModelAdapter? = null
        override fun gatedModel(usage: Usage<*>): ModelAdapter? = null
        override fun <Subject> setContextProvider(
            usage: Usage<Subject>,
            provider: EvaluationContextProvider<Subject>?
        ) = Unit
        override fun setDefaultContextProvider(provider: DefaultEvaluationContextProvider?) = Unit
        override fun setEvaluationObserver(observer: EvaluationObserver?) = Unit
        override fun setModelResolver(resolver: ModelResolver?) = Unit
        override fun registerModelFactory(factory: () -> ModelAdapter) = Unit
        override suspend fun <Subject> fetchContext(
            usage: Usage<Subject>,
            subject: Subject
        ): EvaluationContext = EvaluationContext.EMPTY
    }
}
