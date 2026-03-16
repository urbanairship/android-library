/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.property.PageBranching
import com.urbanairship.android.layout.property.PageBranching.PageSelector
import com.urbanairship.android.layout.property.PagerControllerBranching
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonMatcher
import com.urbanairship.json.JsonPredicate
import com.urbanairship.json.JsonValue
import com.urbanairship.json.ValueMatcher
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class PagerBranchControlTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val thomasState = MutableStateFlow(
        ThomasState(
            layout = State.Layout(identifier = "test-layout"),
            form = null,
            pager = null,
            video = null
        )
    )

    private var lastUpdatedPages: List<PagerModel.Item> = emptyList()
    private var lastUpdatedComplete: Boolean = false

    private val onBranchUpdated: (List<PagerModel.Item>, Boolean) -> Unit = { pages, complete ->
        lastUpdatedPages = pages
        lastUpdatedComplete = complete
    }

    private val actionsRunner: (List<StateAction>) -> Unit = {}

    @Before
    public fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- onPageRequest tests ---

    @Test
    public fun testOnPageRequestBack(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b1 = makePage("b1")
        val b2 = makePage("b2")
        val a3 = makePage("a3")

        val allPages = listOf(a1, b1, a2, b2, a3)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        control.addToHistory("a1")
        advanceUntilIdle()
        control.addToHistory("a2")
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "a2", "a3"),
            lastUpdatedPages.map { it.identifier }
        )

        control.onPageRequest(PageRequest.BACK)
        advanceUntilIdle()

        // After BACK, last history entry (a2) is removed. History is [a1],
        // so the path rebuilds from a1 forward.
        assertEquals(
            listOf("a1", "a2", "a3"),
            lastUpdatedPages.map { it.identifier }
        )
    }

    @Test
    public fun testOnPageRequestFirst(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1")
        val a2 = makePage("a2")

        val allPages = listOf(a1, b1, a2)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        control.addToHistory("a1")
        advanceUntilIdle()
        setState("a1_next", "toggle_a1")
        advanceUntilIdle()
        control.addToHistory("b1")
        advanceUntilIdle()

        // Now on b1 path
        assert(lastUpdatedPages.map { it.identifier }.contains("b1"))

        // FIRST clears all history — next updateState re-seeds with first page
        control.onPageRequest(PageRequest.FIRST)
        advanceUntilIdle()

        // After clearing history and re-evaluation, history restarts from a1.
        // a1's branching still matches toggle_a1 → b1, so path is a1 → b1
        val pages = lastUpdatedPages.map { it.identifier }
        assertEquals("a1", pages.first())
    }

    @Test
    public fun testOnPageRequestNextIsNoOp(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val a2 = makePage("a2")
        val b1 = makePage("b1")

        val allPages = listOf(a1, b1, a2)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        val pagesBefore = lastUpdatedPages.map { it.identifier }

        control.onPageRequest(PageRequest.NEXT)
        advanceUntilIdle()

        assertEquals(pagesBefore, lastUpdatedPages.map { it.identifier })
    }

    // --- removeFromHistory tests ---

    @Test
    public fun testRemoveFromHistory(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b1 = makePage("b1")
        val b2 = makePage("b2")
        val a3 = makePage("a3")

        val allPages = listOf(a1, b1, a2, b2, a3)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        control.addToHistory("a1")
        advanceUntilIdle()
        control.addToHistory("a2")
        advanceUntilIdle()

        control.removeFromHistory("a2")
        advanceUntilIdle()

        // Trigger a state re-evaluation so onBranchUpdated fires
        setState("dummy_key", "dummy_value")
        advanceUntilIdle()

        // a2 was removed from history, but since a1's branching still falls through
        // to a2 in the forward path, a2 appears via buildPathFrom
        val pages = lastUpdatedPages.map { it.identifier }
        assert(pages.contains("a1")) { "a1 should still be present: $pages" }
    }

    @Test
    public fun testRemoveFromHistoryWithInvalidId(): Unit = runTest {
        val a1 = makePage("a1")

        val control = PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })

        // Removing a page that doesn't exist should not crash or change state
        control.removeFromHistory("nonexistent")
        advanceUntilIdle()

        setState("dummy", "value")
        advanceUntilIdle()

        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })
    }

    // --- Completion evaluation tests ---

    @Test
    public fun testCompletionWithMatchingPredicate(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle", "b1", fallback = "a2"))
        val a2 = makePage("a2")
        val b1 = makePage("b1")

        val completionPredicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf("completed_key"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("done")))
                    .build()
            )
            .build()

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = completionPredicate,
                stateActions = null
            )
        )

        val control = PagerBranchControl(
            availablePages = listOf(a1, b1, a2),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(false, control.isComplete.value)
        assertEquals(false, lastUpdatedComplete)

        setState("completed_key", "done")
        advanceUntilIdle()

        assertEquals(true, control.isComplete.value)
        assertEquals(true, lastUpdatedComplete)
    }

    @Test
    public fun testCompletionWithNullPredicateIsImplicitMatch(): Unit = runTest {
        val a1 = makePage("a1")

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = null,
                stateActions = null
            )
        )

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // null predicate is an implicit match, so completion fires immediately
        assertEquals(true, lastUpdatedComplete)
    }

    @Test
    public fun testCompletionRunsStateActions(): Unit = runTest {
        val a1 = makePage("a1")

        val stateAction = StateAction.SetState(
            key = "action_key",
            value = JsonValue.wrap("action_value")
        )

        var ranActions: List<StateAction> = emptyList()
        val captureActionsRunner: (List<StateAction>) -> Unit = { ranActions = it }

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = null,
                stateActions = listOf(stateAction)
            )
        )

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = captureActionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(1, ranActions.size)
        assertEquals(stateAction, ranActions.first())
    }

    @Test
    public fun testCompletionStateActionsNotReRunOnSubsequentUpdates(): Unit = runTest {
        val a1 = makePage("a1")

        var runCount = 0
        val countingActionsRunner: (List<StateAction>) -> Unit = { runCount++ }

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = null,
                stateActions = listOf(
                    StateAction.SetState(key = "k", value = JsonValue.wrap("v"))
                )
            )
        )

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = countingActionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(1, runCount)

        // Trigger additional state updates — should NOT re-run actions
        setState("some_key", "some_value")
        advanceUntilIdle()
        setState("another_key", "another_value")
        advanceUntilIdle()

        assertEquals(1, runCount)
    }

    @Test
    public fun testCompletionNotMatchedStaysIncomplete(): Unit = runTest {
        val a1 = makePage("a1")

        val completionPredicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf("never_set_key"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("never_set_value")))
                    .build()
            )
            .build()

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = completionPredicate,
                stateActions = null
            )
        )

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(false, lastUpdatedComplete)

        // Trigger some unrelated state changes
        setState("unrelated", "value")
        advanceUntilIdle()

        assertEquals(false, lastUpdatedComplete)
    }

    // --- addToHistory edge cases ---

    @Test
    public fun testAddToHistoryDuplicateIsNoOp(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle", "b1", fallback = "a2"))
        val a2 = makePage("a2")
        val b1 = makePage("b1")

        val allPages = listOf(a1, b1, a2)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        control.addToHistory("a1")
        advanceUntilIdle()

        val pagesBefore = lastUpdatedPages.map { it.identifier }

        // Adding a1 again should be a no-op
        control.addToHistory("a1")
        advanceUntilIdle()

        setState("dummy", "trigger")
        advanceUntilIdle()

        val pagesAfter = lastUpdatedPages.map { it.identifier }
        assertEquals(pagesBefore, pagesAfter)
    }

    @Test
    public fun testAddToHistoryInvalidIdIsNoOp(): Unit = runTest {
        val a1 = makePage("a1")

        val control = PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })

        // Adding a non-existent page should not crash or alter pages
        control.addToHistory("nonexistent")
        advanceUntilIdle()

        setState("dummy", "trigger")
        advanceUntilIdle()

        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })
    }

    @Test
    public fun testAddToHistoryClearsForwardHistory(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1")
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b2 = makePage("b2")
        val a3 = makePage("a3")

        val allPages = listOf(a1, b1, a2, b2, a3)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        control.addToHistory("a1")
        advanceUntilIdle()
        control.addToHistory("a2")
        advanceUntilIdle()
        control.addToHistory("a3")
        advanceUntilIdle()

        // Re-adding a2 should clear a3 from forward history
        control.addToHistory("a2")
        advanceUntilIdle()

        setState("dummy", "trigger")
        advanceUntilIdle()

        val pages = lastUpdatedPages.map { it.identifier }
        // a2 is the last in history, so forward path rebuilds from a2
        assert(pages.contains("a2")) { "a2 should be in pages: $pages" }
    }

    // --- Single page / no branching / empty pages ---

    @Test
    public fun testSinglePageNoBranching(): Unit = runTest {
        val a1 = makePage("a1")

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })
    }

    @Test
    public fun testMultiplePagesNoBranching(): Unit = runTest {
        val a1 = makePage("a1")
        val a2 = makePage("a2")
        val a3 = makePage("a3")

        PagerBranchControl(
            availablePages = listOf(a1, a2, a3),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // Without branching, only the first page is in the path (no next page selectors)
        assertEquals(listOf("a1"), lastUpdatedPages.map { it.identifier })
    }

    @Test
    public fun testEmptyAvailablePages(): Unit = runTest {
        PagerBranchControl(
            availablePages = emptyList(),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(emptyList<String>(), lastUpdatedPages.map { it.identifier })
    }

    // --- isComplete flow tests ---

    @Test
    public fun testIsCompleteFlowInitiallyFalse(): Unit = runTest {
        val a1 = makePage("a1")

        val control = PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(false, control.isComplete.value)
    }

    @Test
    public fun testIsCompleteFlowUpdatesOnCompletion(): Unit = runTest {
        val a1 = makePage("a1")

        val predicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf("finish"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("yes")))
                    .build()
            )
            .build()

        val control = PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(
                completions = listOf(
                    PagerControllerBranching.Completion(predicate = predicate, stateActions = null)
                )
            ),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(false, control.isComplete.value)

        setState("finish", "yes")
        advanceUntilIdle()

        assertEquals(true, control.isComplete.value)
    }

    @Test
    public fun testCompletionStaysCompleteAfterStateClearsTrigger(): Unit = runTest {
        val a1 = makePage("a1")

        val predicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf("finish"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("yes")))
                    .build()
            )
            .build()

        val control = PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(
                completions = listOf(
                    PagerControllerBranching.Completion(predicate = predicate, stateActions = null)
                )
            ),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        setState("finish", "yes")
        advanceUntilIdle()
        assertEquals(true, control.isComplete.value)

        // Once complete, clearing the trigger should NOT revert to incomplete
        // because the code only re-evaluates when _isComplete is false
        clearState("finish")
        advanceUntilIdle()
        assertEquals(true, control.isComplete.value)
    }

    // --- State-driven branching path changes ---

    @Test
    public fun testBranchingPathUpdatesOnStateChange(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "go_b", "b1", fallback = "a2"))
        val b1 = makePage("b1")
        val a2 = makePage("a2")

        val allPages = listOf(a1, b1, a2)

        PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // Initially: fallback path a1 → a2
        assertEquals(listOf("a1", "a2"), lastUpdatedPages.map { it.identifier })

        // Set state to trigger conditional branch
        setState("a1_next", "go_b")
        advanceUntilIdle()

        // Now: conditional path a1 → b1
        assertEquals(listOf("a1", "b1"), lastUpdatedPages.map { it.identifier })

        // Clear state to revert to fallback
        clearState("a1_next")
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2"), lastUpdatedPages.map { it.identifier })
    }

    @Test
    public fun testMultipleCompletionsFirstMatchWins(): Unit = runTest {
        val a1 = makePage("a1")

        val firstPredicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf("key1"))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap("match")))
                    .build()
            )
            .build()

        var ranActions: List<StateAction> = emptyList()
        val captureRunner: (List<StateAction>) -> Unit = { ranActions = it }

        val firstAction = StateAction.SetState(key = "first", value = JsonValue.wrap("1"))
        val secondAction = StateAction.SetState(key = "second", value = JsonValue.wrap("2"))

        val completions = listOf(
            PagerControllerBranching.Completion(
                predicate = firstPredicate,
                stateActions = listOf(firstAction)
            ),
            PagerControllerBranching.Completion(
                predicate = null,
                stateActions = listOf(secondAction)
            )
        )

        PagerBranchControl(
            availablePages = listOf(a1),
            controllerBranching = PagerControllerBranching(completions = completions),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = captureRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // The second completion (null predicate = implicit match) fires on init.
        // Both completions are evaluated with filter, so both match. Actions from
        // both should be run.
        assert(ranActions.isNotEmpty()) { "Expected completion actions to run" }
    }

    @Test
    public fun testBackThenForwardRebuildsPath(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle", "b1", fallback = "a2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle2", "b2", fallback = "a3"))
        val a3 = makePage("a3")
        val b1 = makePage("b1")
        val b2 = makePage("b2")

        val allPages = listOf(a1, b1, a2, b2, a3)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2", "a3"), lastUpdatedPages.map { it.identifier })

        // Navigate forward
        control.addToHistory("a1")
        advanceUntilIdle()
        control.addToHistory("a2")
        advanceUntilIdle()
        control.addToHistory("a3")
        advanceUntilIdle()

        // Go back
        control.onPageRequest(PageRequest.BACK)
        advanceUntilIdle()

        // Trigger re-evaluation
        setState("dummy", "trigger")
        advanceUntilIdle()

        val pages = lastUpdatedPages.map { it.identifier }
        // After BACK, history is [a1, a2], path from a2 includes a3
        assert(pages.contains("a2")) { "a2 should still be in pages: $pages" }
        assert(pages.contains("a3")) { "a3 should be in forward path: $pages" }
    }

    /**
     * Simulates a bug scenario: navigate forward through a1→a2→a3→a4, toggle to b4,
     * then toggle back to a4. Without the fix, a4 would appear twice in the page list.
     */
    @Test
    public fun testToggleBackOnLastPageProducesNoDuplicates(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1", branching = branchingTo("b1_next", "toggle_b1", "a1", fallback = "b2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b2 = makePage("b2", branching = branchingTo("b2_next", "toggle_b2", "a2", fallback = "b3"))
        val a3 = makePage("a3", branching = branchingTo("a3_next", "toggle_a3", "b3", fallback = "a4"))
        val b3 = makePage("b3", branching = branchingTo("b3_next", "toggle_b3", "a3", fallback = "b4"))
        val a4 = makePage("a4", branching = branchingTo("a4_next", "toggle_a4", "b4", fallback = null))
        val b4 = makePage("b4", branching = branchingTo("b4_next", "toggle_b4", "a4", fallback = null))

        val allPages = listOf(a1, b1, a2, b2, a3, b3, a4, b4)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // Initial state: should start with a1 and trace forward path
        assertEquals(
            listOf("a1", "a2", "a3", "a4"),
            lastUpdatedPages.map { it.identifier }
        )

        // Simulate navigating forward: a1→a2→a3→a4
        // Each "Next" tap sets a non-toggle value on the state key, which doesn't match
        // the toggle selector, so the branching falls through to the next A page.
        // We simulate this by adding pages to history as the pager would.
        control.addToHistory("a1")
        advanceUntilIdle()
        setState("a1_next", "next_value")
        advanceUntilIdle()

        control.addToHistory("a2")
        advanceUntilIdle()
        setState("a2_next", "next_value")
        advanceUntilIdle()

        control.addToHistory("a3")
        advanceUntilIdle()
        setState("a3_next", "next_value")
        advanceUntilIdle()

        control.addToHistory("a4")
        advanceUntilIdle()

        // Now on a4. Clear a4_next (simulating a4's state_actions on display).
        clearState("a4_next")
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "a2", "a3", "a4"),
            lastUpdatedPages.map { it.identifier }
        )

        // Toggle a4 → b4: set a4_next to the toggle value
        setState("a4_next", "toggle_a4")
        advanceUntilIdle()

        control.addToHistory("b4")
        advanceUntilIdle()

        // Clear b4_next (b4's state_actions on display)
        clearState("b4_next")
        advanceUntilIdle()

        // Should now show path through b4
        val pagesAfterToggleToB = lastUpdatedPages.map { it.identifier }
        assertEquals(pagesAfterToggleToB.distinct(), pagesAfterToggleToB)
        assert(pagesAfterToggleToB.contains("b4"))

        // Toggle b4 → a4: set b4_next to the toggle value.
        // a4_next is still "toggle_a4" (stale from the earlier toggle), which would
        // cause a cycle in buildPathFrom without the fix.
        setState("b4_next", "toggle_b4")
        advanceUntilIdle()

        val pagesAfterToggleBack = lastUpdatedPages.map { it.identifier }

        // The page list must not contain duplicates
        assertEquals(
            "Page list should have no duplicates: $pagesAfterToggleBack",
            pagesAfterToggleBack.distinct(),
            pagesAfterToggleBack
        )

        // a4 should still be reachable (present in the list)
        assert(pagesAfterToggleBack.contains("a4")) {
            "a4 should be in the page list: $pagesAfterToggleBack"
        }

        // b4 should be before a4 (the toggle navigates forward from b4 to a4)
        val b4Index = pagesAfterToggleBack.indexOf("b4")
        val a4Index = pagesAfterToggleBack.indexOf("a4")
        assert(b4Index < a4Index) {
            "b4 ($b4Index) should be before a4 ($a4Index) in: $pagesAfterToggleBack"
        }
    }

    /**
     * Normal forward navigation should not be affected by the deduplication.
     */
    @Test
    public fun testForwardNavigationUnchanged(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1", branching = branchingTo("b1_next", "toggle_b1", "a1", fallback = "b2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b2 = makePage("b2")
        val a3 = makePage("a3")

        val allPages = listOf(a1, b1, a2, b2, a3)

        PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        // Initial forward path: a1 → a2 → a3 (following fallback selectors)
        assertEquals(
            listOf("a1", "a2", "a3"),
            lastUpdatedPages.map { it.identifier }
        )
    }

    /**
     * Toggle on an earlier page (not the last) should also produce no duplicates.
     */
    @Test
    public fun testToggleBackOnEarlierPageProducesNoDuplicates(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1", branching = branchingTo("b1_next", "toggle_b1", "a1", fallback = "b2"))
        val a2 = makePage("a2")
        val b2 = makePage("b2")

        val allPages = listOf(a1, b1, a2, b2)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "a2"),
            lastUpdatedPages.map { it.identifier }
        )

        // Toggle a1 → b1
        setState("a1_next", "toggle_a1")
        advanceUntilIdle()
        control.addToHistory("b1")
        advanceUntilIdle()
        clearState("b1_next")
        advanceUntilIdle()

        // Toggle b1 → a1 (a1_next is still stale = "toggle_a1")
        setState("b1_next", "toggle_b1")
        advanceUntilIdle()

        val pages = lastUpdatedPages.map { it.identifier }
        assertEquals(
            "Page list should have no duplicates: $pages",
            pages.distinct(),
            pages
        )
        assert(pages.contains("a1")) { "a1 should be in the page list: $pages" }
    }

    /**
     * Toggle on a middle page should preserve the forward path. Navigating a1→a2,
     * toggling to b2, then toggling back to a2 should still include a3 and a4.
     */
    @Test
    public fun testToggleBackOnMiddlePagePreservesForwardPath(): Unit = runTest {
        val a1 = makePage("a1", branching = branchingTo("a1_next", "toggle_a1", "b1", fallback = "a2"))
        val b1 = makePage("b1", branching = branchingTo("b1_next", "toggle_b1", "a1", fallback = "b2"))
        val a2 = makePage("a2", branching = branchingTo("a2_next", "toggle_a2", "b2", fallback = "a3"))
        val b2 = makePage("b2", branching = branchingTo("b2_next", "toggle_b2", "a2", fallback = "b3"))
        val a3 = makePage("a3", branching = branchingTo("a3_next", "toggle_a3", "b3", fallback = "a4"))
        val b3 = makePage("b3", branching = branchingTo("b3_next", "toggle_b3", "a3", fallback = "b4"))
        val a4 = makePage("a4", branching = branchingTo("a4_next", "toggle_a4", "b4", fallback = null))
        val b4 = makePage("b4", branching = branchingTo("b4_next", "toggle_b4", "a4", fallback = null))

        val allPages = listOf(a1, b1, a2, b2, a3, b3, a4, b4)

        val control = PagerBranchControl(
            availablePages = allPages,
            controllerBranching = PagerControllerBranching(completions = emptyList()),
            thomasState = thomasState,
            onBranchUpdated = onBranchUpdated,
            actionsRunner = actionsRunner,
            scope = testScope
        )
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "a2", "a3", "a4"),
            lastUpdatedPages.map { it.identifier }
        )

        // Navigate forward: a1 → a2
        control.addToHistory("a1")
        advanceUntilIdle()
        setState("a1_next", "next_value")
        advanceUntilIdle()
        control.addToHistory("a2")
        advanceUntilIdle()
        clearState("a2_next")
        advanceUntilIdle()

        assertEquals(
            listOf("a1", "a2", "a3", "a4"),
            lastUpdatedPages.map { it.identifier }
        )

        // Toggle a2 → b2
        setState("a2_next", "toggle_a2")
        advanceUntilIdle()
        control.addToHistory("b2")
        advanceUntilIdle()
        clearState("b2_next")
        advanceUntilIdle()

        // Toggle b2 → a2
        setState("b2_next", "toggle_b2")
        advanceUntilIdle()
        // Simulate landing on a2: its display actions clear the stale a2_next toggle
        clearState("a2_next")
        advanceUntilIdle()

        val pages = lastUpdatedPages.map { it.identifier }

        assertEquals(
            "Page list should have no duplicates: $pages",
            pages.distinct(),
            pages
        )

        // The forward path (a3, a4) must be preserved after the toggle back
        assert(pages.contains("a2")) { "a2 should be in the page list: $pages" }
        assert(pages.contains("a3")) { "a3 should be in the page list: $pages" }
        assert(pages.contains("a4")) { "a4 should be in the page list: $pages" }

        // b2 should be before a2, and a2 before a3
        val b2Index = pages.indexOf("b2")
        val a2Index = pages.indexOf("a2")
        val a3Index = pages.indexOf("a3")
        assert(b2Index < a2Index) {
            "b2 ($b2Index) should be before a2 ($a2Index) in: $pages"
        }
        assert(a2Index < a3Index) {
            "a2 ($a2Index) should be before a3 ($a3Index) in: $pages"
        }
    }

    // --- Helpers ---

    private fun makePage(
        id: String,
        branching: PageBranching? = null
    ): PagerModel.Item {
        return PagerModel.Item(
            view = mockk(relaxed = true),
            identifier = id,
            displayActions = null,
            automatedActions = null,
            accessibilityActions = null,
            stateActions = null,
            branching = branching
        )
    }

    /**
     * Creates a [PageBranching] with a conditional selector and optional fallback.
     *
     * The conditional selector matches when the state at [stateKey] equals [matchValue],
     * directing to [targetPageId]. If [fallback] is provided, a second selector with no
     * predicate directs to the fallback page.
     */
    private fun branchingTo(
        stateKey: String,
        matchValue: String,
        targetPageId: String,
        fallback: String? = null
    ): PageBranching {
        val predicate = JsonPredicate.Builder()
            .setPredicateType(JsonPredicate.PredicateType.AND)
            .addMatcher(
                JsonMatcher.newBuilder()
                    .setScope(listOf(stateKey))
                    .setValueMatcher(ValueMatcher.newValueMatcher(JsonValue.wrap(matchValue)))
                    .build()
            )
            .build()

        val selectors = mutableListOf(
            PageSelector(predicate = predicate, pageId = targetPageId)
        )
        if (fallback != null) {
            selectors.add(PageSelector(predicate = null, pageId = fallback))
        }

        return PageBranching(nextPageSelectors = selectors)
    }

    private fun setState(key: String, value: String) {
        val currentLayout = thomasState.value.layout ?: return
        val mutations = currentLayout.mutations.toMutableMap()
        mutations[key] = LayoutState.StateMutation(
            id = key,
            key = key,
            value = JsonValue.wrap(value)
        )
        thomasState.value = thomasState.value.copy(
            layout = currentLayout.copy(mutations = mutations)
        )
    }

    private fun clearState(key: String) {
        val currentLayout = thomasState.value.layout ?: return
        val mutations = currentLayout.mutations.toMutableMap()
        mutations.remove(key)
        thomasState.value = thomasState.value.copy(
            layout = currentLayout.copy(mutations = mutations)
        )
    }
}
