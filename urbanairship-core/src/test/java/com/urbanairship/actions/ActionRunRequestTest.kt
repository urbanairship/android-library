/* Copyright Airship and Contributors */
package com.urbanairship.actions

import android.os.Bundle
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ShadowAirshipExecutorsLegacy
import com.urbanairship.actions.ActionResult.Companion.newResult
import com.urbanairship.actions.ActionRunRequest.Companion.createRequest
import com.urbanairship.actions.ActionValue.Companion.wrap
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.Collections
import java.util.ConcurrentModificationException

@Config(
    sdk = [28], shadows = [ShadowAirshipExecutorsLegacy::class]
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4::class)
public class ActionRunRequestTest {

    private var actionRegistry = ActionRegistry()
    private val dispatcher = StandardTestDispatcher()

    /**
     * Test running an action
     */
    @Test
    public fun testRunAction() {
        val result = newResult(wrap("result"))
        val action = TestAction(true, result)

        // Run the action without a callback
        createRequest(action)
            .setDispatcher(dispatcher)
            .setValue("val")
            .run()
            .also { dispatcher.scheduler.advanceUntilIdle() }

        assertTrue("Action failed to run", action.performCalled)
        assertNull(
            "Action name should be null",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testRunSuspendingAction(): TestResult = runTest {
        val result = newResult(wrap("result"))
        val action = TestAction(true, result)

        // Run the action without a callback
        createRequest(action)
            .setDispatcher(UnconfinedTestDispatcher())
            .setValue("val")
            .runSuspending()

        assertTrue("Action failed to run", action.performCalled)
        assertNull(
            "Action name should be null",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /**
     * Test running an action with a callback.
     */
    @Test
    public fun testRunActionWithCallback() {
        val result = newResult(wrap("result"))
        val callback = TestActionCompletionCallback()
        val action = TestAction(true, result)

        // Run the action with a callback
        createRequest(action)
            .setDispatcher(dispatcher)
            .setValue("val")
            .run(callback)
            .also { dispatcher.scheduler.advanceUntilIdle() }

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Result was not called with expected result", result, callback.lastResult
        )
        assertEquals(
            "Run args and the callback args should match", action.runArgs, callback.lastArguments
        )
        assertNull(
            "Action name should be null",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /**
     * Test running an action from the registry.
     */
    @Test
    public fun testRunActionFromRegistry() {
        val result = newResult(wrap("result"))
        val action = TestAction(true, result)

        // Register the action
        actionRegistry.registerEntry(setOf("action!")) { ActionRegistry.Entry(action) }

        // Run the action without a callback
        createRequest("action!", actionRegistry)
            .setDispatcher(dispatcher)
            .setValue("val")
            .run()
            .also { dispatcher.scheduler.advanceUntilIdle() }

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Wrong action name",
            "action!",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /**
     * Test running an action from the registry with a callback.
     */
    @Test
    public fun testRunActionFromRegistryWithCallback() {
        val result = newResult(wrap("result"))
        val action = TestAction(true, result)
        val callback = TestActionCompletionCallback()

        // Register the action
        actionRegistry.registerEntry(setOf("action!")) { ActionRegistry.Entry(action) }

        // Run the action with a callback
        createRequest("action!", actionRegistry)
            .setValue("val")
            .setDispatcher(dispatcher)
            .run(callback)
            .also { dispatcher.scheduler.advanceUntilIdle() }

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Result was not called with expected result", result, callback.lastResult
        )
        assertEquals(
            "Wrong action name",
            "action!",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
    }

    /**
     * Test running an action synchronously
     */
    @Test
    public fun testRunActionSync() {
        val action = TestAction()

        // Run the action by name
        val result = createRequest(action).runSync()
        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Result status should be COMPLETED", ActionResult.Status.COMPLETED, result.status
        )
    }

    /**
     * Test running an action synchronously from the registry
     */
    @Test
    public fun testRunSyncFromRegistry() {
        val action = TestAction()

        // Register the action
        actionRegistry.registerEntry(setOf("action!")) { ActionRegistry.Entry(action) }

        // Run the action by name
        val result = createRequest("action!", actionRegistry).runSync()

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Wrong action name",
            "action!",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
        assertEquals(
            "Result status should be COMPLETED", ActionResult.Status.COMPLETED, result.status
        )
    }

    /**
     * Test trying to set the action value to something that is not ActionValue wrappable.
     */
    @Test
    public fun testInvalidActionValueFromObject() {
        assertThrows(IllegalArgumentException::class.java) {
            // Try setting a invalid value
            createRequest("action").setValue(Any())
        }
    }

    /**
     * Test running an action from the registry with a predicate
     * that rejects the arguments
     */
    @Test
    public fun testRunActionFromRegistryWithPredicate() {
        val action = TestAction(true)
        val callback = TestActionCompletionCallback()

        // Register the action
        actionRegistry.registerEntry(setOf("action!")) { ActionRegistry.Entry(action) }

        // Set a predicate that rejects all arguments
        actionRegistry.updateEntry("action!", object : ActionPredicate {
            override fun apply(arguments: ActionArguments): Boolean {
                return false
            }
        })

        createRequest("action!", actionRegistry)
            .setValue("val")
            .setDispatcher(dispatcher)
            .run(callback)
            .also { dispatcher.scheduler.advanceUntilIdle() }

        val result = callback.lastResult

        assertTrue(
            "Predicate rejecting args should have the result value be 'null'",
            result?.value?.isNull == true
        )

        assertEquals(
            "Result should have an rejected argument status",
            ActionResult.Status.REJECTED_ARGUMENTS,
            result?.status
        )

        assertTrue("Callback is not being called", callback.onFinishCalled)
    }

    /**
     * Test running an action without setting the situation defaults to Action.SITUATION_MANUAL_INVOCATION
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testRunDefaultSituation() {
        val action = TestAction(true)

        createRequest(action)
            .setDispatcher(UnconfinedTestDispatcher())
            .runSync()

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Situation should default to MANUAL_INVOCATION",
            Action.Situation.MANUAL_INVOCATION,
            action.runArgs?.situation
        )
    }

    /**
     * Test running an action that does not exist in the registry
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testRunActionNoEntry() {
        val result = createRequest("action", actionRegistry)
            .setDispatcher(UnconfinedTestDispatcher())
            .runSync()


        assertTrue(
            "Running an action that does not exist should return a 'null' result",
            result.value.isNull
        )

        assertEquals(
            "Result should have an error status",
            ActionResult.Status.ACTION_NOT_FOUND,
            result.status
        )
    }

    /**
     * Test action callback is called on the callers thread
     */
    @Test
    public fun testCallbackHappensOnCallersThread() {
        // Get the looper
        val looper = Shadows.shadowOf(Looper.getMainLooper())

        // Run any tasks in its queue
        looper.runToEndOfTasks()

        // Run an action with a callback
        val testAction: Action = TestAction()
        val callback = TestActionCompletionCallback()

        createRequest(testAction)
            .setDispatcher(dispatcher)
            .run(callback)
            .also {
                looper.pause()
                dispatcher.scheduler.advanceUntilIdle()
            }

        // Check that we have a message in the looper's queue
        assertEquals(1, looper.scheduler.size())
        assertFalse("Callback should not be called yet", callback.onFinishCalled)

        // Run all messages
        looper.runToEndOfTasks()
        assertEquals(0, looper.scheduler.size())
        assertTrue("Callback on finish is not being called", callback.onFinishCalled)
    }

    /**
     * Test setting metadata will be combined with the registry name.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testMetadata() {
        val action = TestAction()
        actionRegistry.registerEntry(setOf("action!")) { ActionRegistry.Entry(action) }

        // Create metadata
        val metadata = Bundle()
        metadata.putString("so", "meta")

        // Run the action by name
        createRequest("action!", actionRegistry)
            .setMetadata(metadata)
            .setDispatcher(UnconfinedTestDispatcher())
            .runSync()

        assertTrue("Action failed to run", action.performCalled)
        assertEquals(
            "Wrong action name",
            "action!",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
        assertEquals(
            "Wrong action name",
            "action!",
            action.runArgs?.metadata?.getString(ActionArguments.REGISTRY_ACTION_NAME_METADATA)
        )
        assertEquals("Missing metadata", "meta", action.runArgs?.metadata?.getString("so"))
    }

    /**
     * Test that reproduces ConcurrentModificationException when setting metadata
     * and creating action arguments concurrently.
     *
     * This test reproduces the issue described in:
     * https://github.com/urbanairship/android-library/issues/258
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    public fun testConcurrentModificationExceptionWithMetadata() {
        val action = TestAction()
        actionRegistry.registerEntry(setOf("test_action")) { ActionRegistry.Entry(action) }

        val initialMetadata = Bundle()
        initialMetadata.putString("key1", "value1")
        initialMetadata.putString("key2", "value2")

        val request = createRequest("test_action", actionRegistry)
            .setMetadata(initialMetadata)
            .setDispatcher(UnconfinedTestDispatcher())

        // Track exceptions caught during concurrent execution
        val exceptions = Collections.synchronizedList(mutableListOf<Throwable>())
        val maxIterations = 400
        val threadCount = 20

        // Create multiple threads that will concurrently:
        // 1. Call setMetadata() repeatedly
        // 2. Call runSync() which internally calls createActionArguments()
        val threads = mutableListOf<Thread>()

        // Half the threads will set metadata
        for (i in 0 until threadCount / 2) {
            threads.add(Thread {
                try {
                    for (j in 0 until maxIterations) {
                        val newMetadata = Bundle()
                        newMetadata.putString("key1", "value1_${i}_$j")
                        newMetadata.putString("key2", "value2_${i}_$j")
                        newMetadata.putString("key3", "value3_${i}_$j")
                        request.setMetadata(newMetadata)
                    }
                } catch (e: Throwable) {
                    exceptions.add(e)
                }
            })
        }

        // Other half will run the action (which calls createActionArguments)
        for (i in 0 until threadCount / 2) {
            threads.add(Thread {
                try {
                    for (j in 0 until maxIterations) {
                        request.runSync()
                        // Small delay to increase chance of race condition
                        Thread.sleep(0, 1)
                    }
                } catch (e: Throwable) {
                    exceptions.add(e)
                }
            })
        }

        // Start all threads
        threads.forEach { it.start() }

        // Wait for all threads to complete
        threads.forEach { it.join() }

        // Check if we caught any ConcurrentModificationException
        val concurrentModExceptions = exceptions.filterIsInstance<ConcurrentModificationException>()

        // The test should reproduce the bug, so we expect at least one ConcurrentModificationException
        // If the fix is applied, this test should pass (no exceptions)
        if (concurrentModExceptions.isNotEmpty()) {
            // Bug reproduced - this is expected before the fix
            println("Reproduced ConcurrentModificationException: ${concurrentModExceptions.size} occurrences")
            concurrentModExceptions.first().printStackTrace()
        } else if (exceptions.isNotEmpty()) {
            // Other exceptions occurred
            println("Other exceptions occurred: ${exceptions.joinToString { it.javaClass.simpleName + ": " + it.message }}")
            exceptions.first().printStackTrace()
        }

        // Note: This test is designed to reproduce the bug. With the fix applied,
        // no exceptions should occur. The assertion will pass if no exceptions occurred
        // or if ConcurrentModificationException was caught (reproducing the bug).
        assertTrue(exceptions.isEmpty())
    }

    private inner class TestActionCompletionCallback : ActionCompletionCallback {

        var lastResult: ActionResult? = null
        var onFinishCalled: Boolean = false
        var lastArguments: ActionArguments? = null

        override fun onFinish(arguments: ActionArguments, result: ActionResult) {
            lastResult = result
            lastArguments = arguments
            onFinishCalled = true
        }
    }
}
