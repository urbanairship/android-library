/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.actions.ActionResult.Companion.newEmptyResult
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.actions.ActionRunRequest.Companion.createRequest
import com.urbanairship.actions.ActionValue.Companion.wrap
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@Config(sdk = [28])
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4::class)
public class SuspendingActionTest {

    private val actionRegistry = ActionRegistry()

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A suspending action that does not complete until its gate is released. */
    private class GatedSuspendingAction(
        private val gate: CompletableDeferred<Unit>,
        private val result: ActionResult = newEmptyResult()
    ) : SuspendingAction() {

        var started: Boolean = false
        var finished: Boolean = false
        var onFinishResult: ActionResult? = null

        override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
            started = true
            gate.await()
            finished = true
            return result
        }

        override fun onFinish(arguments: ActionArguments, result: ActionResult) {
            onFinishResult = result
        }
    }

    /** Test that the framework awaits a suspending action instead of returning an empty result. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testFrameworkAwaitsSuspendingAction(): TestResult = runTest {
        val gate = CompletableDeferred<Unit>()
        val expected = newResult(wrap("done"))
        val action = GatedSuspendingAction(gate, expected)

        var runResult: ActionRunRequest.ActionRunResult? = null
        val job = launch {
            runResult = createRequest(action)
                .setDispatcher(UnconfinedTestDispatcher(testScheduler))
                .setValue("val")
                .runSuspending()
        }

        runCurrent()
        assertTrue("Action should have started", action.started)
        assertFalse("Action should not have completed yet", action.finished)

        gate.complete(Unit)
        job.join()

        assertTrue("Action should have completed", action.finished)
        assertEquals("Result should be the completed result", expected, runResult?.result)
        assertEquals("onFinish should see the completed result", expected, action.onFinishResult)
    }

    /**
     * Test that the registry lookup path also awaits the action. The gate is left open until
     * the request has started, since completing it upfront would pass either way.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testSuspendingActionFromRegistry(): TestResult = runTest {
        val gate = CompletableDeferred<Unit>()
        val expected = newResult(wrap("done"))
        val action = GatedSuspendingAction(gate, expected)
        actionRegistry.registerEntry(setOf("suspending!")) { ActionRegistry.Entry(action) }

        var runResult: ActionRunRequest.ActionRunResult? = null
        val job = launch {
            runResult = createRequest("suspending!", actionRegistry)
                .setDispatcher(UnconfinedTestDispatcher(testScheduler))
                .runSuspending()
        }

        runCurrent()
        assertTrue("Action looked up from the registry should have started", action.started)
        assertFalse("Action should not have completed yet", action.finished)

        gate.complete(Unit)
        job.join()

        assertTrue(action.finished)
        assertEquals(expected, runResult?.result)
        assertEquals(
            "suspending!",
            runResult?.arguments?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /** Test that a rejected suspending action never performs. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testRejectedArguments(): TestResult = runTest {
        val action = object : SuspendingAction() {
            var performCalled: Boolean = false
            override fun acceptsArguments(arguments: ActionArguments): Boolean = false
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                performCalled = true
                return newEmptyResult()
            }
        }

        val runResult = createRequest(action)
            .setDispatcher(UnconfinedTestDispatcher(testScheduler))
            .runSuspending()

        assertFalse(action.performCalled)
        assertEquals(ActionResult.Status.REJECTED_ARGUMENTS, runResult.result.status)
    }

    /** Test that exceptions thrown from a suspending action become error results. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testExceptionBecomesErrorResult(): TestResult = runTest {
        val action = object : SuspendingAction() {
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                throw IllegalStateException("boom")
            }
        }

        val runResult = createRequest(action)
            .setDispatcher(UnconfinedTestDispatcher(testScheduler))
            .runSuspending()

        assertEquals(ActionResult.Status.EXECUTION_ERROR, runResult.result.status)
    }

    /** Test that existing blocking actions still work through the suspending path. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testBlockingActionStillRuns(): TestResult = runTest {
        val expected = newResult(wrap("result"))
        val action = TestAction(true, expected)

        val runResult = createRequest(action)
            .setDispatcher(UnconfinedTestDispatcher(testScheduler))
            .runSuspending()

        assertTrue(action.performCalled)
        assertTrue(action.onStartCalled)
        assertTrue(action.onFinishCalled)
        assertEquals(expected, runResult.result)
    }

    /** Test that the blocking bridge on SuspendingAction returns the completed result. */
    @Test
    public fun testBlockingBridge() {
        val gate = CompletableDeferred<Unit>()
        gate.complete(Unit)
        val expected = newResult(wrap("done"))
        val action = GatedSuspendingAction(gate, expected)

        assertEquals(expected, action.run(ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "val")))
        assertTrue(action.finished)
    }

    /** Test that [ActionRunRequest.runSync] waits for a suspending action's real work. */
    @Test
    public fun testRunSyncAgainstSuspendingAction() {
        val expected = newResult(wrap("done"))
        val action = object : SuspendingAction() {
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                delay(10)
                return expected
            }
        }

        val result = createRequest(action).runSync()

        assertEquals(expected, result)
    }

    /**
     * Test that a main-thread action yields the dispatcher instead of blocking it, so other
     * queued work (`otherWorkRan`) can still run while it's suspended.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testMainThreadActionSuspendsWithoutBlocking(): TestResult = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))

        val gate = CompletableDeferred<Unit>()
        val action = object : SuspendingAction() {
            var started: Boolean = false
            override fun shouldRunOnMainThread(): Boolean = true
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                started = true
                gate.await()
                return newEmptyResult()
            }
        }

        val job = launch {
            createRequest(action).runSuspending()
        }

        var otherWorkRan = false
        launch(Dispatchers.Main) {
            otherWorkRan = true
        }

        runCurrent()
        assertTrue("Action should have started on the main dispatcher", action.started)
        assertFalse("run() must not complete while the action is suspended", job.isCompleted)
        assertTrue(
            "Other work queued on the same dispatcher must still run while the action is suspended",
            otherWorkRan
        )

        gate.complete(Unit)
        runCurrent()
        assertTrue("run() must complete once the action finishes", job.isCompleted)
    }

    /**
     * Test that [Action.runSuspending] rethrows a [CancellationException] raised inside
     * [Action.performSuspending], instead of swallowing it into an error result. Thrown
     * directly rather than via `job.cancel()`: a cancelled Job finalizes as cancelled either
     * way, so that wouldn't exercise the catch branch.
     *
     * This only covers cancellation from inside the action: [ActionRunRequest] runs actions on
     * their own scope, so cancelling a caller going through it doesn't reach the action itself.
     */
    @Test
    public fun testCancellationFromPerformSuspendingPropagates(): TestResult = runTest {
        val action = object : SuspendingAction() {
            var onFinishCalled: Boolean = false
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                throw CancellationException("boom")
            }
            override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                onFinishCalled = true
            }
        }

        var caught: CancellationException? = null
        try {
            action.runSuspending(ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "val"))
        } catch (e: CancellationException) {
            caught = e
        }

        assertTrue("runSuspending must rethrow CancellationException, not swallow it", caught != null)
        assertFalse("onFinish should not run when the action is cancelled", action.onFinishCalled)
    }
}
