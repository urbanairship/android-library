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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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

    /**
     * The framework must await a suspending action, so the reported result is the completed
     * result rather than an empty placeholder.
     */
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

    /** The registry lookup path must also take the suspending path. */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testSuspendingActionFromRegistry(): TestResult = runTest {
        val gate = CompletableDeferred<Unit>()
        val expected = newResult(wrap("done"))
        val action = GatedSuspendingAction(gate, expected)
        actionRegistry.registerEntry(setOf("suspending!")) { ActionRegistry.Entry(action) }

        gate.complete(Unit)
        val runResult = createRequest("suspending!", actionRegistry)
            .setDispatcher(UnconfinedTestDispatcher(testScheduler))
            .runSuspending()

        assertTrue(action.finished)
        assertEquals(expected, runResult.result)
        assertEquals(
            "suspending!",
            runResult.arguments.metadata.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /** A rejected suspending action never performs. */
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

    /** Exceptions thrown from a suspending action become error results. */
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

    /** Existing blocking actions keep working through the same path. */
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

    /** The blocking bridge on SuspendingAction still returns the completed result. */
    @Test
    public fun testBlockingBridge() {
        val gate = CompletableDeferred<Unit>()
        gate.complete(Unit)
        val expected = newResult(wrap("done"))
        val action = GatedSuspendingAction(gate, expected)

        assertEquals(expected, action.run(ActionTestUtils.createArgs(Action.Situation.MANUAL_INVOCATION, "val")))
        assertTrue(action.finished)
    }

    /**
     * [ActionRunRequest.runSync] blocks the calling thread, but it must still wait for a
     * suspending action's real work rather than returning as soon as it suspends.
     */
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
     * A main-thread action ([Action.shouldRunOnMainThread] == true) that suspends must yield
     * control instead of blocking, so other work queued on the same dispatcher can still run
     * while it's suspended.
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
            createRequest(action).setDispatcher(UnconfinedTestDispatcher(testScheduler)).runSuspending()
        }

        runCurrent()
        assertTrue("Action should have started on the main dispatcher", action.started)
        assertFalse("run() must not complete while the action is suspended", job.isCompleted)

        gate.complete(Unit)
        runCurrent()
        assertTrue("run() must complete once the action finishes", job.isCompleted)
    }

    /**
     * Cancelling the caller must propagate as a real cancellation, not get swallowed into an
     * error [ActionResult] the way other exceptions are.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testCancellationPropagates(): TestResult = runTest {
        val started = CompletableDeferred<Unit>()
        val action = object : SuspendingAction() {
            var onFinishCalled: Boolean = false
            override suspend fun performSuspending(arguments: ActionArguments): ActionResult {
                started.complete(Unit)
                awaitCancellation()
            }
            override fun onFinish(arguments: ActionArguments, result: ActionResult) {
                onFinishCalled = true
            }
        }

        val job = launch {
            createRequest(action).setDispatcher(UnconfinedTestDispatcher(testScheduler)).runSuspending()
        }

        started.await()
        job.cancel()
        job.join()

        assertTrue("The caller's job should be cancelled, not completed with a result", job.isCancelled)
        assertFalse("onFinish should not run for a cancelled action", action.onFinishCalled)
    }
}
